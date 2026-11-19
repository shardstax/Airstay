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

$assets = @(
    @{ File = 'hotel-exterior.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2001' },
    @{ File = 'room.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2002' },
    @{ File = 'pool.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2003' },
    @{ File = 'pune.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2101' },
    @{ File = 'hyderabad.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2102' },
    @{ File = 'bengaluru.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2103' },
    @{ File = 'delhi.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2104' },
    @{ File = 'mumbai.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2105' },
    @{ File = 'nagpur.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2106' },
    @{ File = 'jaipur.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2107' },
    @{ File = 'king.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2201' },
    @{ File = 'twin.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2202' },
    @{ File = 'family.jpg'; Id = 'b23ab7ce-0246-4f3f-92c1-49ea58bb2203' }
)

$images = foreach ($asset in $assets) {
    $path = Join-Path $PSScriptRoot "demo-assets\$($asset.File)"
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing demo image: $path"
    }
    $hex = [BitConverter]::ToString([IO.File]::ReadAllBytes($path)).Replace('-', '')
    "('$($asset.Id)', 'image/jpeg', decode('$hex', 'hex'))"
}

$attach = @'
UPDATE hotel
SET photos = ARRAY[
  CASE city
    WHEN 'Pune, Maharashtra, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2101'
    WHEN 'Hyderabad, Telangana, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2102'
    WHEN 'Bengaluru, Karnataka, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2103'
    WHEN 'Delhi, National Capital Territory of Delhi, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2104'
    WHEN 'Mumbai, Maharashtra, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2105'
    WHEN 'Nagpur, Maharashtra, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2106'
    WHEN 'Jaipur, Rajasthan, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2107'
  END,
  '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2001',
  '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2003'
]
WHERE owner_id = :demo_owner_id;

UPDATE room r
SET photos = ARRAY[
  CASE
    WHEN r.capacity >= 4 THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2203'
    WHEN r.type ILIKE '%Twin%' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2202'
    ELSE '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2201'
  END,
  '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2002'
]
FROM hotel h
WHERE h.id = r.hotel_id
  AND h.owner_id = :demo_owner_id;
'@

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
