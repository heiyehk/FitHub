param(
  [int]$X,
  [int]$Y,
  [switch]$Force,
  [int]$TimeoutSec = 30
)

# 把模拟器主窗口夹回屏幕内。
#
# 为什么需要它：模拟器 36.3.10 每次启动都会把窗口摆到 y = -525（顶边在屏幕上方 525 像素），
# 标题栏完全在屏幕外 —— 抓不到标题栏就没法拖动，窗口顶部那几百像素的内容也永远看不见。
#
# 三个环节缺一不可，少一个都会漏：
#
#   1. 等窗口定型。窗口刚创建时在 (0,0)，emulator 之后才把它摆到最终位置。只看第一眼就会
#      得出"已经在屏幕内了"的结论，然后 1 秒后窗口才跑到屏幕外面去 —— 上一版就是这么漏掉
#      问题的，报了 "already fully on screen" 却什么也没修。
#   2. 四条边都要在屏幕工作区内。只判左上角是不行的：窗口在 (2600,100) 时右边越界 463 像素，
#      旧脚本照样认为它没问题。
#   3. 比工作区高的窗口要先缩小。AVD 是 1080x2400，不缩放的话 2400 > 工作区 1716，
#      位置修得再对，底部也永远看不全。
#
# 坐标系：pwsh 起来时是 DPI-unaware，Win32 窗口 API 和 Screen 都会被虚拟化到逻辑像素
# （本机 2880x1800 @175% 会被报成 1646x1029）。虚拟化本身对 GetWindowRect/MoveWindow
# 是自洽的，但打印出来的尺寸会和 AVD 的 1080x2400 差 1.75 倍，下次谁来读这段输出都会被带偏。
# 所以先切到 Per-Monitor V2，之后所有数字都是物理像素。

$ErrorActionPreference = 'Stop'

if (-not ('FitHubDpi' -as [type])) {
  Add-Type @"
using System;
using System.Runtime.InteropServices;
public class FitHubDpi {
  [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr v);
  [DllImport("user32.dll")] public static extern IntPtr GetThreadDpiAwarenessContext();
  [DllImport("user32.dll")] public static extern int GetAwarenessFromDpiAwarenessContext(IntPtr v);
}
"@
}
# 尽力切到 Per-Monitor V2，但不要拿它的返回值当依据：进程起来晚了系统可能已经拒绝修改
# （返回 false），而加载 Windows.Forms 之后又可能已经被别的代码设成 aware 了。
# 实测下来两种情况下拿到的都是物理像素，只有返回值不一致 —— 所以这里改成直接问当前状态。
[void][FitHubDpi]::SetProcessDpiAwarenessContext([IntPtr](-4))
$awareness = [FitHubDpi]::GetAwarenessFromDpiAwarenessContext([FitHubDpi]::GetThreadDpiAwarenessContext())
$script:DpiAware = ($awareness -ge 1)   # 1=system aware, 2=per-monitor aware, 0=unaware

Add-Type -AssemblyName System.Windows.Forms

if (-not ('FitHubEmuWin' -as [type])) {
  Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public class FitHubEmuWin {
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int ht, bool repaint);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
}

function Get-EmuMainWindow {
  # 只按进程找会命中一堆 DummyWin / IME / ToolSaveBits，全是 0x0 或不可见的辅助窗口。
  # 主窗口的特征是 class=Qt653QWindowIcon + 可见 + 属于 emulator/qemu 进程；
  # 这三个条件还不够 —— 同进程还有一个不可见的 Qt653QWindowIcon，所以取面积最大的那个。
  $script:bestHwnd = [IntPtr]::Zero
  $script:bestArea = 0
  $cb = [FitHubEmuWin+EnumProc]{
    param($h, $p)
    [uint32]$pd = 0
    [void][FitHubEmuWin]::GetWindowThreadProcessId($h, [ref]$pd)
    $pr = Get-Process -Id $pd -ErrorAction SilentlyContinue
    if ($pr -and $pr.ProcessName -match '^(emulator|qemu-system-.*)$') {
      $c = New-Object Text.StringBuilder 256
      [void][FitHubEmuWin]::GetClassName($h, $c, 256)
      if ($c.ToString() -eq 'Qt653QWindowIcon' -and [FitHubEmuWin]::IsWindowVisible($h)) {
        $r = New-Object FitHubEmuWin+RECT
        [void][FitHubEmuWin]::GetWindowRect($h, [ref]$r)
        $area = ($r.Right - $r.Left) * ($r.Bottom - $r.Top)
        if ($area -gt $script:bestArea) {
          $script:bestArea = $area
          $script:bestHwnd = $h
        }
      }
    }
    return $true
  }
  [void][FitHubEmuWin]::EnumWindows($cb, [IntPtr]::Zero)
  return $script:bestHwnd
}

function Get-Rect {
  param([IntPtr]$Hwnd)
  $r = New-Object FitHubEmuWin+RECT
  [void][FitHubEmuWin]::GetWindowRect($Hwnd, [ref]$r)
  [pscustomobject]@{
    L = $r.Left; T = $r.Top; R = $r.Right; B = $r.Bottom
    W = $r.Right - $r.Left; H = $r.Bottom - $r.Top
  }
}

function Get-WorkArea {
  param([int]$Left, [int]$Top, [int]$Right, [int]$Bottom)
  # 选与窗口重叠最多的那块屏幕；完全在屏幕外时（正是这个脚本要修的那种情况）
  # 重叠面积为 0，就退回主屏。
  $best = $null
  $bestArea = 0
  foreach ($s in [System.Windows.Forms.Screen]::AllScreens) {
    $b = $s.WorkingArea
    $ix = [Math]::Min($Right, $b.Right) - [Math]::Max($Left, $b.Left)
    $iy = [Math]::Min($Bottom, $b.Bottom) - [Math]::Max($Top, $b.Top)
    $area = if ($ix -gt 0 -and $iy -gt 0) { $ix * $iy } else { 0 }
    if ($area -gt $bestArea) { $bestArea = $area; $best = $b }
  }
  if (-not $best) { $best = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea }
  return $best
}

function Test-Fits {
  param($Rect, $Area)
  ($Rect.L -ge $Area.Left) -and ($Rect.T -ge $Area.Top) -and
  ($Rect.R -le $Area.Right) -and ($Rect.B -le $Area.Bottom)
}

# 1) 等窗口出现
$deadline = (Get-Date).AddSeconds($TimeoutSec)
$hwnd = [IntPtr]::Zero
while ((Get-Date) -lt $deadline) {
  $hwnd = Get-EmuMainWindow
  if ($hwnd -ne [IntPtr]::Zero) { break }
  Start-Sleep -Milliseconds 250
}
if ($hwnd -eq [IntPtr]::Zero) {
  Write-Output "no visible emulator main window found (is the AVD running?)"
  exit 1
}

# 2) 等位置定型：连续 3 次采样（约 1.2s）不变才算数
$lastKey = ''
$stable = 0
$settleUntil = (Get-Date).AddSeconds(15)
$rect = $null
while ((Get-Date) -lt $settleUntil) {
  $rect = Get-Rect $hwnd
  $key = "$($rect.L),$($rect.T),$($rect.W),$($rect.H)"
  if ($key -eq $lastKey) { $stable++ } else { $stable = 0; $lastKey = $key }
  if ($stable -ge 3) { break }
  Start-Sleep -Milliseconds 400
}

$unit = if ($script:DpiAware) { 'physical px' } else { 'logical px (DPI-unaware)' }
$wa = Get-WorkArea $rect.L $rect.T $rect.R $rect.B
Write-Output "settled: ($($rect.L),$($rect.T)) $($rect.W)x$($rect.H)   work area: ($($wa.Left),$($wa.Top)) $($wa.Width)x$($wa.Height)   [$unit]"

# 3) 夹进去，夹完再复查 —— 只在越界时才动，用户自己拖到别的位置不去抢
$moved = $false
$final = $null
for ($try = 1; $try -le 5; $try++) {
  $nw = [Math]::Min($rect.W, $wa.Width)
  $nh = [Math]::Min($rect.H, $wa.Height)

  # 只把越界的那条边拉回来，不动用户自己摆好的另一边
  $nx = $rect.L
  $ny = $rect.T
  if ($nx -lt $wa.Left) { $nx = $wa.Left }
  if ($ny -lt $wa.Top) { $ny = $wa.Top }
  if ($nx + $nw -gt $wa.Right) { $nx = $wa.Right - $nw }
  if ($ny + $nh -gt $wa.Bottom) { $ny = $wa.Bottom - $nh }

  if ($Force) {
    $nx = if ($PSBoundParameters.ContainsKey('X')) { $X } else { $wa.Left + [int](($wa.Width - $nw) / 2) }
    $ny = if ($PSBoundParameters.ContainsKey('Y')) { $Y } else { $wa.Top + [int](($wa.Height - $nh) / 2) }
    $nx = [Math]::Max($wa.Left, [Math]::Min($nx, $wa.Right - $nw))
    $ny = [Math]::Max($wa.Top, [Math]::Min($ny, $wa.Bottom - $nh))
  }

  if (($rect.L -ne $nx) -or ($rect.T -ne $ny) -or ($rect.W -ne $nw) -or ($rect.H -ne $nh)) {
    [void][FitHubEmuWin]::MoveWindow($hwnd, $nx, $ny, $nw, $nh, $true)
    $moved = $true
    Write-Output ("moved  : ($nx,$ny) ${nw}x${nh}" + $(if ($try -gt 1) { "  (attempt $try)" } else { '' }))
  }

  Start-Sleep -Milliseconds 800
  $check = Get-Rect $hwnd
  $wa = Get-WorkArea $check.L $check.T $check.R $check.B
  if (Test-Fits $check $wa) { $final = $check; break }
  Write-Output "window went off screen again after clamping, retrying..."
  $rect = $check
}

if (-not $final) {
  $final = Get-Rect $hwnd
  Write-Output "WARNING: could not keep the window on screen, last position ($($final.L),$($final.T))"
}
if (-not $moved) { Write-Output "already fully on screen, left it alone (use -Force to move anyway)" }
[void][FitHubEmuWin]::SetForegroundWindow($hwnd)
Write-Output "after  : ($($final.L),$($final.T))-($($final.R),$($final.B))"
