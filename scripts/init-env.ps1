# Creates .env with freshly generated random secrets, and the folders the site stores its data in.
# It never overwrites an existing .env and never prints the secrets.
param(
    # Where the database files, collected memes and backups live. Needs plenty of free space.
    [string]$DataDir = 'D:/usethatmeme-data'
)
$ErrorActionPreference = 'Stop'

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'

# One folder per kind of data, so each can be backed up or cleaned on its own.
function New-DataFolders([string]$root) {
    foreach ($name in 'postgres', 'images', 'inbox', 'logs', 'backups') {
        New-Item -ItemType Directory -Force -Path (Join-Path $root $name) | Out-Null
    }
}

if (Test-Path $envFile) {
    Write-Host ".env already exists; leaving its secrets untouched."
    $existing = Get-Content $envFile
    if (-not ($existing | Where-Object { $_ -like 'USETHATMEME_DATA_DIR=*' })) {
        # An .env from before the data folder existed: add just that one setting.
        Add-Content -Path $envFile -Value "USETHATMEME_DATA_DIR=$DataDir" -Encoding ascii
        Write-Host "Added USETHATMEME_DATA_DIR=$DataDir to .env."
        New-DataFolders $DataDir
    }
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
    "USETHATMEME_DATA_DIR=$DataDir",
    'DB_USERNAME=usethatmeme',
    "DB_PASSWORD=$(New-Secret 24)",
    "S3_ACCESS_KEY=$(New-Secret 12)",
    "S3_SECRET_KEY=$(New-Secret 24)",
    "USETHATMEME_JWT_SECRET=$(New-Secret 48)",
    'USETHATMEME_ADMIN_USERNAME=admin',
    "USETHATMEME_ADMIN_PASSWORD=$(New-Secret 18)"
)
# ASCII without BOM: a BOM would corrupt the first key for docker-compose and Spring.
Set-Content -Path $envFile -Value $lines -Encoding ascii
New-DataFolders $DataDir
Write-Host "Created $envFile with new random secrets, and the data folders under $DataDir."
