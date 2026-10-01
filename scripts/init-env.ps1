# Creates .env with freshly generated random secrets.
# It never overwrites an existing .env and never prints the secrets.
$ErrorActionPreference = 'Stop'

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (Test-Path $envFile) {
    Write-Host ".env already exists; leaving it untouched."
    exit 0
}

# URL-safe characters only, so the values are safe in .env, YAML and docker-compose.
function New-Secret([int]$byteCount) {
    $bytes = New-Object byte[] $byteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$lines = @(
    'DB_USERNAME=memehub',
    "DB_PASSWORD=$(New-Secret 24)",
    "S3_ACCESS_KEY=$(New-Secret 12)",
    "S3_SECRET_KEY=$(New-Secret 24)",
    "MEMEHUB_JWT_SECRET=$(New-Secret 48)",
    'MEMEHUB_ADMIN_USERNAME=admin',
    "MEMEHUB_ADMIN_PASSWORD=$(New-Secret 18)"
)
# ASCII without BOM: a BOM would corrupt the first key for docker-compose and Spring.
Set-Content -Path $envFile -Value $lines -Encoding ascii
Write-Host "Created $envFile with new random secrets."
