# Captures one native window by process id, so the installed launcher can be inspected without
# grabbing the whole desktop. PrintWindow asks the window to draw itself, which also works when
# another window overlaps it.
#
#   powershell -NoProfile -File tools/window-shot.ps1 -ProcessId 9604 -OutFile shot.png
param(
	[int]$ProcessId,
	[string]$OutFile
)

Add-Type -AssemblyName System.Drawing

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public class WinShot
{
    [DllImport("user32.dll")]
    public static extern bool PrintWindow(IntPtr hwnd, IntPtr hdc, uint flags);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

$process = Get-Process -Id $ProcessId -ErrorAction Stop
$handle = $process.MainWindowHandle

if ($handle -eq [IntPtr]::Zero) {
	Write-Output "no window for pid $ProcessId"
	exit 1
}

$rect = New-Object WinShot+RECT
[void][WinShot]::GetWindowRect($handle, [ref]$rect)
$width = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top

$bitmap = New-Object System.Drawing.Bitmap $width, $height
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$hdc = $graphics.GetHdc()
$ok = [WinShot]::PrintWindow($handle, $hdc, 2)   # 2 = PW_RENDERFULLCONTENT
$graphics.ReleaseHdc($hdc)

if (-not $ok) {
	Write-Output "PrintWindow refused; copying from the screen instead"
	$graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size $width, $height))
}

$graphics.Dispose()
$bitmap.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()

Write-Output "WINDOW-SHOT pid=$ProcessId size=${width}x${height} file=$OutFile"
