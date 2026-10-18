# Copies the upload keystore to the clipboard as base64, ready to paste into the
# GitHub repository secret KEYSTORE_BASE64.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\export-keystore-for-ci.ps1
#
# The value is a *secret*: it lets whoever holds it sign updates for your app.
# Only paste it into a private repository's Actions secrets.

$ErrorActionPreference = "Stop"

$root  = Split-Path -Parent $PSScriptRoot
$store = Join-Path $root "keystore\upload-keystore.jks"

if (-not (Test-Path $store)) {
    Write-Error "Keystore not found at $store"
    exit 1
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($store))

try {
    Set-Clipboard -Value $b64
    Write-Host "Copied base64 keystore to the clipboard ($($b64.Length) characters)." -ForegroundColor Green
} catch {
    Write-Warning "Could not access the clipboard; printing instead."
}

Write-Host ""
Write-Host "Now add these repository secrets (Settings -> Secrets and variables -> Actions):"
Write-Output  ""
Write-Output "  KEYSTORE_BASE64     <- the value below"
Write-Output "  KEYSTORE_PASSWORD   <- from keystore.properties"
Write-Output "  KEY_ALIAS           -> upload"
Write-Output "  KEY_PASSWORD        <- same as KEYSTORE_PASSWORD"
Write-Output ""

# Print only on explicit request so the secret does not linger in scrollback.
if ($args -contains "-Print") {
    Write-Output $b64
} else {
    Write-Host "The base64 blob is on your clipboard (not printed here on purpose)." -ForegroundColor Yellow
    Write-Host "Re-run with -Print to print it instead." -ForegroundColor DarkGray
}
