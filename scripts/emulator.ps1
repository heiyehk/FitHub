param(
  [string]$Avd = 'Medium_Phone_API_36.1',
  [switch]$Snapshot,
  [int]$X,
  [int]$Y,
  [int]$BootWaitSec = 0
)

# 启动模拟器，并把窗口夹回屏幕内。
#
# 为什么必须夹：emulator 36.3.10 没有 -window-position 之类的参数（-help-all 里能影响
# 初始尺寸的只有 -fixed-scale，-scale 早就 obsolete 被忽略），窗口位置是它自己在 Qt 层
# 算的，而且算错了 —— 每次启动都稳定出现在 y = -525（顶边在屏幕上方 525 像素），
# 标题栏完全抓不到，所以拖不动，顶部几百像素的内容也永远看不见。
#
# 为什么不能靠改配置：~/.android/avd/*/emulator-user.ini 里的 window.x / window.y
# 实测完全不被读。把它写成 y = -900 之后启动，窗口仍然出现在 -525；干净退出之后
# 这两个键也没有被更新过。所以没有"下次启动就正常"的配置文件可改，只能每次启动时夹一次。
#
# 夹完是稳的：开机完成后（实测 154s）窗口没有再被挪动过，位置能一直保持到下次退出。

$ErrorActionPreference = 'Stop'

$emu = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
if (-not (Test-Path $emu)) { throw "emulator not found: $emu" }

$running = Get-Process -Name 'emulator', 'qemu-system-x86_64', 'qemu-system-aarch64' -ErrorAction SilentlyContinue
if ($running) {
  Write-Output "emulator already running, not starting a second one"
} else {
  $emuArgs = @('-avd', $Avd, '-no-boot-anim')
  if (-not $Snapshot) { $emuArgs += '-no-snapshot-load' }
  Write-Output "launch: $emu $($emuArgs -join ' ')"
  Start-Process -FilePath $emu -ArgumentList $emuArgs -WindowStyle Minimized | Out-Null
}

$clamp = @{}
if ($PSBoundParameters.ContainsKey('X')) { $clamp['X'] = $X }
if ($PSBoundParameters.ContainsKey('Y')) { $clamp['Y'] = $Y }
& (Join-Path $PSScriptRoot 'emu-window.ps1') @clamp

if ($BootWaitSec -gt 0) {
  Write-Output "waiting for boot (up to ${BootWaitSec}s)..."
  $deadline = (Get-Date).AddSeconds($BootWaitSec)
  while ((Get-Date) -lt $deadline) {
    $state = (& adb -s emulator-5554 shell getprop sys.boot_completed 2>$null) -join ''
    if ($state.Trim() -eq '1') { Write-Output "booted"; break }
    Start-Sleep -Seconds 2
  }
}
