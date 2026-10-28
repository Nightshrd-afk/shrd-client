# Signs Shrd Launcher's Windows binaries (exe/msi) with an Authenticode signature.
#
# launcher/sign-builds.sh supplies the key pair (a .pfx) and this signs with it. Everything is done
# from memory: the certificate is loaded with EphemeralKeySet, so nothing is written to the Windows
# certificate store, nothing needs administrator rights, and no key can be left behind on the user's
# machine by a build.
#
# Three details it has to get right, each found by running it:
#   * A key imported through the certificate API can be marked "protected by password / UI", which
#     makes every signature open a Windows Security dialog. In a build script that is a silent hang.
#     An ephemeral key never prompts.
#   * jpackage marks its exe read-only, and a signature cannot be appended to a read-only file.
#   * A self-signed certificate verifies as UnknownError: the signature block is correct and matches
#     the file, but Windows cannot chain it to a trusted root. Windows only accepts a new root after
#     a human confirms a dialog, so the status is reported for what it is instead of being hidden.
param(
    [string]$List = "",
    [string]$Pfx = "",
    [string]$PfxPassword = "",
    [string]$ExportCer = ""
)

$ErrorActionPreference = 'Stop'

function Report([string]$line) { Write-Host $line }

if (-not $Pfx) {
    Report "SIGN-FAIL no certificate given"
    exit 1
}

if (-not (Test-Path -LiteralPath $Pfx)) {
    Report "SIGN-FAIL missing $Pfx"
    exit 1
}

$flags = [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet
$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($Pfx, $PfxPassword, $flags)

if (-not $cert.HasPrivateKey) {
    Report "SIGN-FAIL the certificate in $Pfx has no private key"
    exit 1
}

Report "SIGN-CERT $($cert.Thumbprint) $($cert.Subject) (expires $($cert.NotAfter.ToString('yyyy-MM-dd')))"

if ($ExportCer) {
    # The public half, so it can be installed by hand if the publisher name matters on this machine.
    [System.IO.File]::WriteAllBytes($ExportCer, $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
    Report "SIGN-CER $ExportCer"
}

$files = @()

if ($List) {
    $files = $List -split ';' | Where-Object { $_ -and $_.Trim() }
}

if ($files.Count -eq 0) {
    Report "SIGN-FAIL nothing to sign"
    exit 1
}

$failed = 0

foreach ($file in $files) {
    if (-not (Test-Path -LiteralPath $file)) {
        Report "SIGN-FAIL missing $file"
        $failed++
        continue
    }

    $item = Get-Item -LiteralPath $file -Force
    $readOnly = $item.IsReadOnly

    if ($readOnly) {
        $item.IsReadOnly = $false
    }

    try {
        $signature = Set-AuthenticodeSignature -FilePath $file -Certificate $cert -HashAlgorithm SHA256

        if ($signature.Status -ne 'Valid' -and $signature.Status -ne 'UnknownError') {
            Report "SIGN-FAIL $file status=$($signature.Status) $($signature.StatusMessage)"
            $failed++
        }
    } finally {
        if ($readOnly) {
            (Get-Item -LiteralPath $file -Force).IsReadOnly = $true
        }
    }
}

# Read every file back: a signature that was written is not a signature Windows accepts.
foreach ($file in $files) {
    if (-not (Test-Path -LiteralPath $file)) {
        continue
    }

    $check = Get-AuthenticodeSignature -LiteralPath $file

    if ("$($check.Status)" -eq 'Valid') {
        Report "VERIFY $file trusted, publisher=$($check.SignerCertificate.Subject)"
    } elseif ("$($check.Status)" -eq 'UnknownError') {
        Report "VERIFY $file signature valid, certificate not trusted by Windows: $($check.SignerCertificate.Subject)"
    } else {
        Report "VERIFY $file FAILED status=$($check.Status) $($check.StatusMessage)"
        $failed++
    }
}

if ($failed -gt 0) {
    Report "SIGN-FAIL $failed file(s) did not sign or did not verify"
    exit 1
}

Report "SIGN-OK $($files.Count) file(s) signed"
exit 0
