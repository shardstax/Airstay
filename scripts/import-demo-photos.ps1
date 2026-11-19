# Imports the demo catalog and photo library in one transaction after Flyway has run.
param(
    [switch]$Remote,
    [string]$OwnerEmail
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if ($Remote) {
    if ([string]::IsNullOrWhiteSpace($OwnerEmail) -or $OwnerEmail -eq 'demo-catalog-owner@airstay.invalid') {
        throw 'Remote import requires -OwnerEmail for an existing, dedicated hotel manager account.'
    }
    foreach ($variable in 'PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER') {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variable))) {
            throw "Set $variable before running the remote demo import."
        }
    }
    if ($env:PGSSLMODE -notin 'require', 'verify-ca', 'verify-full') {
        throw 'Set PGSSLMODE=require or a certificate-verifying mode for remote import.'
    }
    $null = Get-Command psql -CommandType Application
} elseif ($OwnerEmail) {
    throw 'Use -Remote with -OwnerEmail. The local Compose import uses the local demo owner.'
}

$assets = Import-Csv (Join-Path $PSScriptRoot 'demo-photos.csv')

$images = foreach ($asset in $assets) {
    $path = Join-Path $PSScriptRoot "demo-assets\$($asset.File)"
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing demo image: $path"
    }
    $hex = [BitConverter]::ToString([IO.File]::ReadAllBytes($path)).Replace('-', '')
    "('$($asset.Id)', 'image/jpeg', decode('$hex', 'hex'))"
}

$attach = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'attach-demo-photos.sql'))

$seed = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'seed-demo-catalog.sql'))
$insertImages = 'INSERT INTO catalog_photo (id, media_type, content) VALUES ' +
    ($images -join ",`n") + ' ON CONFLICT (id) DO NOTHING;'
$sql = @('\encoding UTF8', $seed, $insertImages, $attach) -join "`n"
if ($Remote) {
    $sql | psql -X -v ON_ERROR_STOP=1 -v "demo_owner_email=$($OwnerEmail.Trim().ToLowerInvariant())" -1 -f -
} else {
    $sql | docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U airstay -d airstay -1 -f -
}
if ($LASTEXITCODE -ne 0) {
    throw 'Could not import the demo catalog and photos.'
}

Write-Output 'Demo photos imported and attached to the seeded catalog.'
