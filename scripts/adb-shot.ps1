param(
  [Parameter(Mandatory = $true)][string]$Out,
  # 留空则依次找：PATH 上的 adb -> ANDROID_HOME -> ANDROID_SDK_ROOT -> LOCALAPPDATA。
  # 不要在这里写死某台机器的绝对路径 —— 那等于把本机用户名提交进仓库，换台机器就断。
  [string]$Adb = ''
)

if (-not $Adb) {
    $candidates = @()
    $onPath = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if ($onPath) { $candidates += $onPath }
    if ($env:ANDROID_HOME)     { $candidates += "$env:ANDROID_HOME\platform-tools\adb.exe" }
    if ($env:ANDROID_SDK_ROOT) { $candidates += "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" }
    $candidates += "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    $Adb = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $Adb) {
        throw "找不到 adb.exe。装好 Android SDK，或用 -Adb 指定完整路径。已查：$($candidates -join '  |  ')"
    }
}
Write-Host "adb: $Adb" -ForegroundColor DarkGray

# adb exec-out 是二进制流。PowerShell 的 `>` 会对其做文本处理（换行/编码转换），
# 写出来的 PNG 与设备上的字节数不一致 —— 表现为"截图损坏"或"APK 校验失败"，
# 而真因是脚本自己把文件改坏了。所以必须走 BaseStream 原样落盘。
$psi = [Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $Adb
$psi.Arguments = 'exec-out screencap -p'
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$p = [Diagnostics.Process]::Start($psi)
$ms = New-Object IO.MemoryStream
$p.StandardOutput.BaseStream.CopyTo($ms)
$err = $p.StandardError.ReadToEnd()
$p.WaitForExit()
if ($p.ExitCode -ne 0) { throw "screencap failed: $err" }
$bytes = $ms.ToArray()
if ($bytes.Length -lt 1000) { throw "screencap returned only $($bytes.Length) bytes" }
# PNG magic 校验：防止"文件写出来了但是坏的"这种假成功
if ($bytes[0] -ne 0x89 -or $bytes[1] -ne 0x50 -or $bytes[2] -ne 0x4E -or $bytes[3] -ne 0x47) {
  throw "output is not a PNG (magic=$($bytes[0..3] -join ','))"
}
[IO.File]::WriteAllBytes($Out, $bytes)
Write-Output "shot -> $Out ($($bytes.Length) bytes)"
