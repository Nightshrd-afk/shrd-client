<#
.SYNOPSIS
  Drives a running Shrd Client development client and captures screenshots of its UI.

.DESCRIPTION
  Finds the Minecraft window, brings it to the foreground, optionally presses a key or runs a
  client command through chat, then saves a screenshot of the game window. Pair the output with
  tools/UiCheck.java to assert that the Shrd Client theme colours are actually on screen.

.PARAMETER Key
  Optional key to press first: rightshift (opens the ClickGUI), escape, or none.

.PARAMETER Command
  Optional client command to run through chat, e.g. "/shrd gui". Requires the window to have focus.

.EXAMPLE
  powershell -File tools/verify-ui.ps1 -Key rightshift -Output run/screenshots/clickgui.png
  java tools/UiCheck.java run/screenshots/clickgui.png accent,text,textDim 30

.EXAMPLE
  powershell -File tools/verify-ui.ps1 -Output run/screenshots/hud.png
  java tools/UiCheck.java run/screenshots/hud.png accent,text
#>
param(
    [string]$Key = "none",
    [string]$Command = "",
    [string]$Output = "run/screenshots/capture.png",
    [int]$DelaySeconds = 4,
    [int]$SettleSeconds = 3
)

Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class ShrdWin32 {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

function Get-MinecraftWindow {
    # Match the game window itself: a java.exe process whose title starts with "Minecraft".
    # Filtering on title alone would happily match a browser tab about Minecraft.
    Get-Process -Name java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle -like 'Minecraft*' } |
        Select-Object -First 1
}

function Send-Key([byte]$virtualKey, [int]$holdMs = 70) {
    [ShrdWin32]::keybd_event($virtualKey, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds $holdMs
    [ShrdWin32]::keybd_event($virtualKey, 0, 2, [UIntPtr]::Zero)
}

function Focus-Game($window) {
    $handle = $window.MainWindowHandle
    [void][ShrdWin32]::ShowWindow($handle, 9)      # SW_RESTORE
    [void][ShrdWin32]::SetForegroundWindow($handle)
    Start-Sleep -Milliseconds 900

    # A tap of SHIFT nudges keyboard focus into the game without changing game state.
    Send-Key 0x10 60
    [void][ShrdWin32]::SetForegroundWindow($handle)
    Start-Sleep -Milliseconds 500

    return ([ShrdWin32]::GetForegroundWindow() -eq $handle)
}

Write-Host "Waiting for the Minecraft window..."
$deadline = (Get-Date).AddSeconds(240)
$window = $null

while ((Get-Date) -lt $deadline) {
    $window = Get-MinecraftWindow
    if ($window) { break }
    Start-Sleep -Seconds 2
}

if (-not $window) {
    Write-Error "No Minecraft window appeared within 240 seconds."
    exit 2
}

Write-Host ("Found window: '" + $window.MainWindowTitle + "' (pid " + $window.Id + ")")
Start-Sleep -Seconds $DelaySeconds

$focused = Focus-Game $window
Write-Host ("Game focused: " + $focused)

if (-not $focused) {
    Write-Warning "The game window is not in the foreground; the capture may show another window."
}

switch ($Key.ToLower()) {
    "rightshift" { Write-Host "Pressing Right Shift (ClickGUI)"; Send-Key 0xA1; Start-Sleep -Seconds $SettleSeconds }
    "escape"     { Write-Host "Pressing Escape"; Send-Key 0x1B; Start-Sleep -Seconds $SettleSeconds }
    "none"       { }
    default      { Write-Warning ("Unknown key '" + $Key + "', sending nothing.") }
}

if ($Command -ne "") {
    Write-Host ("Sending chat command: " + $Command)
    Send-Key 0x54              # 't' opens the chat box
    Start-Sleep -Milliseconds 700
    [System.Windows.Forms.SendKeys]::SendWait($Command)
    Start-Sleep -Milliseconds 400
    Send-Key 0x0D              # Enter submits
    Start-Sleep -Seconds $SettleSeconds
}

$rect = New-Object ShrdWin32+RECT
[void][ShrdWin32]::GetWindowRect($window.MainWindowHandle, [ref]$rect)

$width = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top

if ($width -le 0 -or $height -le 0) {
    Write-Error "Window has no usable size ($width x $height)."
    exit 2
}

$bitmap = New-Object System.Drawing.Bitmap $width, $height
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)

$directory = Split-Path -Parent $Output
if ($directory -and -not (Test-Path $directory)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

$fullPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Output))
$bitmap.Save($fullPath, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()

Write-Host ("Saved " + $fullPath + " (" + $width + "x" + $height + ")")
