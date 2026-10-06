#Requires -Version 5.1
<#
.SYNOPSIS
    为 FitHub 生成专用的 release keystore，并写出 keystore.properties。

.DESCRIPTION
    口令在你自己终端里交互输入，不回显，也不作为命令行参数传给 keytool
    （走 -storepass:env）。请在本机终端直接运行，不要把口令贴进聊天或 CI 日志。

    换过签名之后，任何已安装的旧包都装不上新包（applicationId 相同、签名不同），
    模拟器或真机上需要先 adb uninstall com.heiyehk.fithub。

    keystore 一旦生成就不能再改：换密钥 = 所有已发布的包都无法覆盖升级。
    所以口令请自己存好，keystore.properties 和 .jks 都已在 .gitignore 中。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\make-release-keystore.ps1
#>

[CmdletBinding()]
param(
    [string]$Alias = 'fithub',
    [int]$ValidDays = 10000,
    [string]$KeystorePath = '',
    [string]$PropertiesPath = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$propsPath = if ($PropertiesPath) { [IO.Path]::GetFullPath($PropertiesPath) } else { Join-Path $repoRoot 'keystore.properties' }
$jksPath   = if ($KeystorePath) { [IO.Path]::GetFullPath($KeystorePath) } else { Join-Path $repoRoot 'app\fithub-release.jks' }

function ConvertTo-PlainText {
    param([Security.SecureString]$Secure)
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# ---- 找 keytool --------------------------------------------------------
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $candidates = Get-ChildItem -Path 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe',
                                    'D:\Android Studio\jbr\bin\keytool.exe',
                                    "$env:LOCALAPPDATA\Android\Sdk" -Recurse -Filter 'keytool.exe' `
                                 -ErrorAction SilentlyContinue | Select-Object -First 1
    $keytool = $candidates.FullName
}
if (-not $keytool) {
    throw '找不到 keytool.exe。请先装好 JDK，或把它的 bin 目录加进 PATH。'
}
Write-Host "keytool: $keytool" -ForegroundColor DarkGray

# ---- 已有文件处理 ------------------------------------------------------
# -Force 必须**把旧 .jks 移走**，光放行不够。keytool -genkeypair 在目标文件已存在时
# 会先加载它，用 -storepass 指定的**新**口令去解**旧**文件（PKCS12 整体加密）⇒
# java.io.IOException: keystore password was incorrect，退出码 1。
# 实测对照：目标存在时退出码 1，目标不存在时同一条命令退出码 0。
# 所以移走旧文件不是保险起见，是 keytool 能跑通的前提条件。
if (Test-Path $jksPath) {
    if (-not $Force) {
        throw "$jksPath 已存在。确认要覆盖就加 -Force（旧密钥对应的已发布包将无法覆盖升级）。"
    }
    $jksBak = "$jksPath.$(Get-Date -Format 'yyyyMMdd-HHmmss').bak"
    Move-Item $jksPath $jksBak
    Write-Host "已备份旧 keystore -> $jksBak" -ForegroundColor Yellow
}

if (Test-Path $propsPath) {
    $old = (Get-Content $propsPath -Encoding UTF8 | Where-Object { $_ -match '^\s*storeFile\s*=' })
    $bak = "$propsPath.bak"
    Copy-Item $propsPath $bak -Force
    Write-Host "已备份现有 keystore.properties -> $bak" -ForegroundColor Yellow
    if ($old -match 'debug\.keystore') {
        Write-Host "  注意：原配置指向 debug.keystore。换正式签名后，模拟器/真机上" -ForegroundColor Yellow
        Write-Host "        已装的旧包需要先卸载，否则装不上。" -ForegroundColor Yellow
    }
}

# ---- 取口令 ------------------------------------------------------------
# 已有 FITHUB_KS_PASS 就直接用（CI / 自动化场景，口令不落命令行）；
# 否则交互式问两次，输入不回显。最终统一放进 $env:FITHUB_KS_PASS 传给 keytool。
$plain1 = $env:FITHUB_KS_PASS
if ([string]::IsNullOrEmpty($plain1)) {
    Write-Host ''
    Write-Host '口令请至少 6 位。PKCS12 要求 store 口令与 key 口令相同，只设一个。' -ForegroundColor Cyan
    $s1 = Read-Host 'release keystore 口令' -AsSecureString
    $s2 = Read-Host '再输一次' -AsSecureString
    $plain1 = ConvertTo-PlainText $s1
    $plain2 = ConvertTo-PlainText $s2
    if ($plain1 -ne $plain2) { throw '两次输入不一致。' }
}
if ($plain1.Length -lt 6) { throw '口令太短，至少 6 位。' }

# 口令只经环境变量传给 keytool，不出现在命令行 / 进程列表里。
# 注意：这个环境变量必须一直活到下面的验证步骤结束，所以清理放在脚本末尾。
$env:FITHUB_KS_PASS = $plain1
Write-Host ''
Write-Host '生成 keystore ...' -ForegroundColor Cyan
& $keytool -genkeypair -v `
    -keystore $jksPath `
    -storetype PKCS12 `
    -alias $Alias `
    -keyalg RSA -keysize 4096 -sigalg SHA256withRSA `
    -validity $ValidDays `
    -dname 'CN=FitHub, OU=Open Source, O=FitHub, L=Unspecified, ST=Unspecified, C=CN' `
    -storepass:env FITHUB_KS_PASS `
    -keypass:env FITHUB_KS_PASS
if ($LASTEXITCODE -ne 0) {
    Remove-Item Env:\FITHUB_KS_PASS -ErrorAction SilentlyContinue
    throw "keytool 失败，退出码 $LASTEXITCODE"
}

# ---- 写 keystore.properties -------------------------------------------
# storeFile 相对**仓库根**解析（app/build.gradle.kts 里用的是 rootProject.file）
$rel = 'app/fithub-release.jks'

$props = @(
    '# release 签名配置。已 git-ignore，**不要提交，也不要提交生成的 .jks**',
    '# 由 scripts/make-release-keystore.ps1 生成',
    "storeFile=$rel",
    "storePassword=$plain1",
    "keyAlias=$Alias",
    "keyPassword=$plain1"
)
[IO.File]::WriteAllLines($propsPath, $props, (New-Object Text.UTF8Encoding $false))

# ---- 验证 --------------------------------------------------------------
# 只依赖字面量正则，不 grep keytool 的英文标签 —— keytool 输出跟随系统 locale，
# 在中文 Windows 上 "Alias name" / "Entry type" 是本地化的，grep 英文会静默匹配不到。
Write-Host ''
Write-Host '验证 ...' -ForegroundColor Cyan
$list = & $keytool -list -v -keystore $jksPath -storepass:env FITHUB_KS_PASS 2>&1
if ($LASTEXITCODE -ne 0) { throw "keytool -list 失败，退出码 $LASTEXITCODE" }

$finger = ($list | Select-String -Pattern 'SHA256:\s*([0-9A-F:]{40,})' | Select-Object -First 1)
if (-not $finger) { throw '验证失败：读不到证书指纹（SHA256 摘要行）。keystore 可能已损坏。' }
$algo = ($list | Select-String -Pattern '(SHA\d+withRSA|SHA\d+withECDSA)' | Select-Object -First 1)
$size = (Get-Item $jksPath).Length

Remove-Item Env:\FITHUB_KS_PASS -ErrorAction SilentlyContinue

Write-Host ''
Write-Host '完成。' -ForegroundColor Green
Write-Host "  keystore : $jksPath  ($size bytes)"
Write-Host "  配置     : $propsPath   (alias=$Alias)"
Write-Host "  签名算法 : $(if ($algo) { $algo.Matches[0].Value } else { '未识别' })"
Write-Host "  证书指纹 : SHA256: $($finger.Matches[0].Groups[1].Value)"
Write-Host ''
Write-Host '下一步：' -ForegroundColor Cyan
Write-Host '  1. 记下上面这行证书指纹 —— 应用商店上架要用它，丢了就只能重新上架'
Write-Host '  2. 把 keystore.properties 的内容和 .jks 一起备份到本机密码管理器'
Write-Host '  3. 卸载模拟器/真机上的旧包，再装新的 release 包'
Write-Host ''
