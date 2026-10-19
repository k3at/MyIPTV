# Pushes the four GitHub Actions signing secrets straight to the repository
# with the GitHub CLI, so the values never have to be copied and pasted by hand.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\set-github-secrets.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\set-github-secrets.ps1 -Repo someone/MyIPTV
#
# Requirements:
#   * gh installed          winget install --id GitHub.cli -e
#   * signed in             gh auth login
#
# Everything this uploads is a SECRET. It is read from keystore.properties and
# keystore\upload-keystore.jks and is never printed.

param(
    [string]$Repo = "k3at/MyIPTV"
)

$ErrorActionPreference = "Stop"

$root  = Split-Path -Parent $PSScriptRoot
$store = Join-Path $root "keystore\upload-keystore.jks"
$conf  = Join-Path $root "keystore.properties"

if (-not (Test-Path $store)) { Write-Error "Keystore not found at $store"; exit 1 }
if (-not (Test-Path $conf))  { Write-Error "keystore.properties not found at $conf - it holds the passwords."; exit 1 }

# ---- locate gh ----
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) {
    foreach ($candidate in @(
        "C:\Program Files\GitHub CLI\gh.exe",
        "$env:LOCALAPPDATA\Programs\GitHub CLI\gh.exe"
    )) {
        if (Test-Path $candidate) { $gh = $candidate; break }
    }
}
if (-not $gh) {
    Write-Host "GitHub CLI (gh) was not found." -ForegroundColor Red
    Write-Host "Install it with:  winget install --id GitHub.cli -e" -ForegroundColor Cyan
    exit 1
}

& $gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "You are not signed in to GitHub yet." -ForegroundColor Yellow
    Write-Host "Run this once, then run this script again:" -ForegroundColor Yellow
    Write-Host "    gh auth login" -ForegroundColor Cyan
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

$storePassword = $settings['storePassword']
$keyAlias      = $settings['keyAlias']
$keyPassword   = $settings['keyPassword']

if (-not $storePassword) { Write-Error "keystore.properties has no storePassword."; exit 1 }
if (-not $keyAlias)      { Write-Error "keystore.properties has no keyAlias.";     exit 1 }
if (-not $keyPassword)   { $keyPassword = $storePassword }

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($store))

$secrets = [ordered]@{
    KEYSTORE_BASE64   = $base64
    KEYSTORE_PASSWORD = $storePassword
    KEY_ALIAS         = $keyAlias
    KEY_PASSWORD      = $keyPassword
}

Write-Host ""
Write-Host " Uploading signing secrets to $Repo" -ForegroundColor Cyan
Write-Host " (from keystore.properties and keystore\upload-keystore.jks)" -ForegroundColor DarkGray
Write-Host ""

foreach ($name in $secrets.Keys) {
    $value = $secrets[$name]
    Write-Host ("  {0,-18} {1,5} chars  " -f $name, $value.Length) -NoNewline
    # --body (not stdin) so no trailing newline can sneak into the value.
    & $gh secret set $name --repo $Repo --body $value 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED" -ForegroundColor Red
        Write-Host "  re-run without piping to Out-Null to see why" -ForegroundColor DarkGray
        exit 1
    }
    Write-Host "ok" -ForegroundColor Green
}

# An earlier hand-made secret under a stray name is not used by the workflow.
$list = & $gh secret list --repo $Repo 2>$null
if ($list | Where-Object { $_ -match '^K3AT\s' }) {
    Write-Host "`n  removing the unused 'K3AT' secret  " -NoNewline
    & $gh secret delete K3AT --repo $Repo 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host "ok" -ForegroundColor Green } else { Write-Host "skipped" -ForegroundColor DarkGray }
}

Write-Host "`n Secrets now on ${Repo}:" -ForegroundColor Cyan
& $gh secret list --repo $Repo
Write-Host ""
Write-Host " Expect four rows above, all named KEY*." -ForegroundColor Yellow
Write-Host " Next: push a tag to build and publish the release." -ForegroundColor Yellow
Write-Host ""
