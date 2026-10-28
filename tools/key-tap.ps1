<#
.SYNOPSIS
  Holds a key in the focused Minecraft window, then screenshots it.
.DESCRIPTION
  Shrd Client samples keys once per client tick, so a key must be held for at least ~150 ms to
  register. This helper holds the key long enough and captures the window afterwards.
.EXAMPLE
  powershell -File tools/key-tap.ps1 -Key rightshift -HoldMs 400 -Output run/screenshots/clickgui.png
#>
param(
    [string]$Key = "rightshift",
    [int]$HoldMs = 400,
    [string]$Output = "run/screenshots/capture.png",
    [int]$SettleSeconds = 2
)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class ShrdTap {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

$codes = @{ rightshift = 0xA1; escape = 0x1B; c = 0x43; g = 0x47; v = 0x56; f6 = 0x75 }

# Extended keys must be sent with KEYEVENTF_EXTENDEDKEY, otherwise Windows reports the
# generic key instead: right shift would arrive as left shift and Minecraft would never
# see the binding fire.
$extendedKeys = @('rightshift')
$KEYEVENTF_EXTENDEDKEY = 0x0001
$KEYEVENTF_KEYUP = 0x0002

if (-not $codes.ContainsKey($Key.ToLower())) {
    Write-Error ("unsupported key '" + $Key + "'; known: " + ($codes.Keys -join ', '))
    exit 2
}

$window = Get-Process -Name java -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle -like 'Minecraft*' } |
    Select-Object -First 1

if (-not $window) { Write-Error "no Minecraft window"; exit 2 }

$handle = $window.MainWindowHandle
[void][ShrdTap]::ShowWindow($handle, 9)
[void][ShrdTap]::SetForegroundWindow($handle)
Start-Sleep -Milliseconds 800

# Latch keyboard focus into the game without touching game state.
[ShrdTap]::keybd_event(0x10, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 80
[ShrdTap]::keybd_event(0x10, 0, 2, [UIntPtr]::Zero)
[void][ShrdTap]::SetForegroundWindow($handle)
Start-Sleep -Milliseconds 500

$vk = $codes[$Key.ToLower()]
$downFlags = if ($extendedKeys -contains $Key.ToLower()) { $KEYEVENTF_EXTENDEDKEY } else { 0 }
$upFlags = [uint32]($downFlags -bor $KEYEVENTF_KEYUP)

Write-Host ("Holding key " + $Key + " for " + $HoldMs + " ms (focused: " + ([ShrdTap]::GetForegroundWindow() -eq $handle) + ")")
[ShrdTap]::keybd_event($vk, 0, $downFlags, [UIntPtr]::Zero)
Start-Sleep -Milliseconds $HoldMs
[ShrdTap]::keybd_event($vk, 0, $upFlags, [UIntPtr]::Zero)
Start-Sleep -Seconds $SettleSeconds

$rect = New-Object ShrdTap+RECT
[void][ShrdTap]::GetWindowRect($handle, [ref]$rect)
$bitmap = New-Object System.Drawing.Bitmap ($rect.Right - $rect.Left), ($rect.Bottom - $rect.Top)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)

$directory = Split-Path -Parent $Output
if ($directory -and -not (Test-Path $directory)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }

$fullPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Output))
$bitmap.Save($fullPath, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose(); $bitmap.Dispose()
Write-Host ("Saved " + $fullPath)
