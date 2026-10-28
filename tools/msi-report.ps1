<#
.SYNOPSIS
  Reports what a Shrd Launcher MSI declares, by reading its own tables.

.DESCRIPTION
  Opens the .msi as a database and prints the facts that matter for a Windows installer: product
  identity, whether it is a per-machine install, the resolved install path, the Start Menu
  shortcut and its folder, and the upgrade codes that let a newer release replace an older one.

  Three dialect traps, each of which silently produces nonsense:
    * queries are written in the Windows Installer's own SQL, where identifiers are backquoted;
    * every SQL string is single-quoted in PowerShell, so a backtick stays a backtick;
    * rows are handed to a scriptblock field by field, because PowerShell unrolls (and mangles)
      nested arrays on the way out of a function.

  jpackage writes short 8.3 names for every directory and file ("zzdvfjsq|Shrd Launcher"), so
  everything printed here is the long half of that pair.

.EXAMPLE
  powershell -File tools/msi-report.ps1 "dist/Shrd Launcher-1.0.0.msi"
#>
param(
    [Parameter(Mandatory = $true)][string]$Msi
)

$ErrorActionPreference = "Stop"
$path = (Resolve-Path $Msi).Path
Write-Host "msi: $path ($((Get-Item $path).Length) bytes)"

$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.GetType().InvokeMember("OpenDatabase", "InvokeMethod", $null, $installer, @($path, 0))

function ForEachRow([string]$sql, [scriptblock]$body) {
    $view = $database.GetType().InvokeMember("OpenView", "InvokeMethod", $null, $database, @($sql))
    $view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, $null)
    while ($true) {
        $record = $view.GetType().InvokeMember("Fetch", "InvokeMethod", $null, $view, $null)
        if ($null -eq $record) { break }
        $count = $record.GetType().InvokeMember("FieldCount", "GetProperty", $null, $record, $null)
        if ($null -eq $count -or $count -lt 1) { continue }
        $fields = @()
        for ($i = 1; $i -le $count; $i++) {
            $fields += ([string]$record.GetType().InvokeMember("StringData", "GetProperty", $null, $record, @($i)))
        }
        & $body $fields
    }
    $view.GetType().InvokeMember("Close", "InvokeMethod", $null, $view, $null)
}

function LongName([string]$field) {
    $parts = $field -split '\|'
    return $parts[$parts.Count - 1]
}

Write-Host ""
Write-Host "== product =="
$wanted = @('ProductName', 'ProductVersion', 'ProductCode', 'Manufacturer', 'ALLUSERS', 'UpgradeCode', 'ARPPRODUCTICON', 'ARPNOMODIFY')
ForEachRow 'SELECT `Property`,`Value` FROM `Property`' {
    param($f)
    if ($wanted -contains $f[0]) { Write-Host ("  {0} = {1}" -f $f[0], $f[1]) }
}

# The directory tree is a parent/child table; walking it is the only way to know where a file or
# shortcut really ends up, because the parent is stored as an opaque directory id.
$parents = @{}
$names = @{}
ForEachRow 'SELECT `Directory`,`Directory_Parent`,`DefaultDir` FROM `Directory`' {
    param($f)
    $parents[$f[0]] = $f[1]
    $names[$f[0]] = (LongName $f[2])
}

function DirectoryPath([string]$id) {
    # The well known system folders carry a '.' as their name, which would hide the fact that the
    # app lands in Program Files; spelled out, the report reads like the path a user would see.
    $known = @{
        'ProgramFilesFolder' = 'Program Files'
        'ProgramFiles64Folder' = 'Program Files'
        'ProgramMenuFolder' = 'Start Menu\Programs'
        'DesktopFolder' = 'Desktop'
    }
    $parts = @()
    $current = $id
    $guard = 0
    while ($current -and $guard -lt 20) {
        if ($known.ContainsKey($current)) {
            $parts = @($known[$current]) + $parts
        } elseif ($names.ContainsKey($current) -and $names[$current] -ne '.' -and $current -ne 'TARGETDIR') {
            $parts = @($names[$current]) + $parts
        }
        $current = $parents[$current]
        $guard++
    }
    return ($parts -join '\')
}

Write-Host ""
Write-Host "== where things land =="
ForEachRow 'SELECT `Directory`,`Directory_Parent`,`DefaultDir` FROM `Directory`' {
    param($f)
    $resolved = DirectoryPath $f[0]
    if ($resolved -like '*Shrd*') {
        Write-Host ("  {0}" -f $resolved)
    }
}

Write-Host ""
Write-Host "== shortcuts =="
ForEachRow 'SELECT `Shortcut`,`Directory_`,`Name`,`Target` FROM `Shortcut`' {
    param($f)
    Write-Host ("  '{0}'  in  {1}" -f (LongName $f[2]), (DirectoryPath $f[1]))
}

Write-Host ""
Write-Host "== upgrades =="
ForEachRow 'SELECT `UpgradeCode`,`VersionMin`,`VersionMax`,`ActionProperty` FROM `Upgrade`' {
    param($f)
    Write-Host ("  {0}  min={1} max={2} action={3}" -f $f[0], $f[1], $f[2], $f[3])
}

Write-Host ""
Write-Host "== the launcher executable, and where its component installs =="
$componentDirs = @{}
ForEachRow 'SELECT `Component`,`Directory_` FROM `Component`' {
    param($f)
    $componentDirs[$f[0]] = $f[1]
}
ForEachRow 'SELECT `File`,`FileName`,`FileSize`,`Component_` FROM `File`' {
    param($f)
    $name = LongName $f[1]
    if ($name -like '*.exe' -and $name -like '*Shrd*') {
        $where = if ($componentDirs.ContainsKey($f[3])) { DirectoryPath $componentDirs[$f[3]] } else { '?' }
        Write-Host ("  {0}  ({1} bytes)  in  {2}" -f $name, $f[2], $where)
    }
}
