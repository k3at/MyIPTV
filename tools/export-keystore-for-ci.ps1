# Prints the four GitHub Actions secrets needed by .github/workflows/release.yml,
# reading the values straight out of keystore.properties.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\export-keystore-for-ci.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\export-keystore-for-ci.ps1 -Print
#
# The base64 keystore is copied to the clipboard (it is ~3 KB, too long to read
# on screen); pass -Print if you also want it echoed.
#
# Everything this prints is a SECRET: whoever holds these values can sign updates
# for your app. Never paste the output into issues, chats or a public repo.

param(
    [switch]$Print
)

$ErrorActionPreference = "Stop"

$root  = Split-Path -Parent $PSScriptRoot
$store = Join-Path $root "keystore\upload-keystore.jks"
$conf  = Join-Path $root "keystore.properties"

if (-not (Test-Path $store)) {
    Write-Error "Keystore not found at $store"
    exit 1
}

if (-not (Test-Path $conf)) {
    Write-Error "keystore.properties not found at $conf - it holds the passwords."
    exit 1
}

# ---- read keystore.properties (one key=value per line) ----
$settings = @{}
foreach ($line in Get-Content $conf) {
    $i = $line.IndexOf('=')
    if ($i -gt 0) {
        $settings[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
    }
}

$password = $settings['storePassword']
$alias    = $settings['keyAlias']
$keyPass  = $settings['keyPassword']

if (-not $password) { Write-Error "keystore.properties has no storePassword."; exit 1 }
if (-not $alias)    { Write-Error "keystore.properties has no keyAlias.";     exit 1 }
if (-not $keyPass)  { $keyPass = $password }

# ---- base64 of the keystore ----
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($store))

$copied = $false
try {
    Set-Clipboard -Value $b64
    $copied = $true
} catch {
    # No clipboard access (e.g. a remote session) - fall back to printing it.
    $Print = $true
}

$pad = 18

Write-Host ""
Write-Host " Repository secrets for .github/workflows/release.yml" -ForegroundColor Cyan
Write-Host " Settings -> Secrets and variables -> Actions -> New repository secret" -ForegroundColor DarkGray
Write-Host ""
Write-Host ("  {0} {1}" -f "NAME".PadRight($pad), "VALUE") -ForegroundColor DarkGray
Write-Host ("  {0} {1}" -f ("-" * ($pad - 2)), ("-" * 5)) -ForegroundColor DarkGray

Write-Host ("  {0} " -f "KEYSTORE_BASE64".PadRight($pad)) -NoNewline
if ($copied) {
    Write-Host "(on your clipboard)" -ForegroundColor Green
} else {
    Write-Host ""
}

Write-Host ("  {0} {1}" -f "KEYSTORE_PASSWORD".PadRight($pad), $password)
Write-Host ("  {0} {1}" -f "KEY_ALIAS".PadRight($pad), $alias)
Write-Host ("  {0} {1}" -f "KEY_PASSWORD".PadRight($pad), $keyPass)
Write-Host ""

if ($Print -and -not $copied) {
    Write-Host " KEYSTORE_BASE64:" -ForegroundColor Cyan
    Write-Output $b64
    Write-Host ""
} elseif ($Print) {
    Write-Host " KEYSTORE_BASE64 (also on your clipboard):" -ForegroundColor Cyan
    Write-Output $b64
    Write-Host ""
}

Write-Host " These values are secrets - do not share or commit them." -ForegroundColor Yellow
Write-Host ""
