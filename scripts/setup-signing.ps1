# LumaShade secure signing setup (run locally on Windows).
$ErrorActionPreference = "Stop"
$keytool = Get-Command "keytool.exe" -ErrorAction SilentlyContinue
if (-not $keytool) { $keytool = Get-Command "keytool" -ErrorAction SilentlyContinue }
if (-not $keytool) { throw "Java keytool not found. Install JDK 17 and reopen PowerShell." }
$dir = Join-Path $env:USERPROFILE "Documents\LumaShadeSigning"
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$store = Join-Path $dir "lumashade-signing.p12"
if (-not (Test-Path $store)) {
    Write-Host "Create a password you will keep securely for every future LumaShade update." -ForegroundColor Yellow
    & $keytool.Source -genkeypair -storetype PKCS12 -keystore $store -alias lumashade -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=LumaShade,O=StrokeDot,C=PH"
    if ($LASTEXITCODE -ne 0) { throw "Failed to create key." }
} else {
    Write-Host "Using existing signing key. Do NOT replace it!" -ForegroundColor Yellow
}
$encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes($store))
Set-Clipboard -Value $encoded
Write-Host "Back up this private key securely: $store" -ForegroundColor Yellow
Write-Host "GitHub Actions secrets: https://github.com/StrokeDot/ExtraDimNative/settings/secrets/actions"
Write-Host "1. Create LUMASHADE_KEYSTORE_BASE64 with clipboard contents."
Write-Host "2. Create LUMASHADE_SIGNING_PASSWORD with the password you entered in keytool."
Write-Host "3. Run the workflow: Publish signed LumaShade update."
Write-Host "Never paste this private key or password into a chat, issue, or commit." -ForegroundColor Yellow
