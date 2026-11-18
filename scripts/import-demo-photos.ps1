# Imports the original varied demo-image library into the local Compose database and attaches it to every
# hotel and room created by seed-demo-catalog.sql. This bypasses the browser only to automate demo setup;
# the resulting rows have the exact same catalog_photo schema and /api/v1/photos/{uuid} URLs as normal uploads.
#
# Prerequisite: docker compose is healthy and scripts/seed-demo-catalog.sql has already been run.

$ErrorActionPreference = 'Stop'

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

foreach ($asset in $assets) {
    $path = Join-Path $PSScriptRoot "demo-assets\$($asset.File)"
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing demo image: $path"
    }
    docker compose cp $path "postgres:/tmp/$($asset.File)"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not copy $($asset.File) into the PostgreSQL container."
    }
}

$sql = @'
INSERT INTO catalog_photo (id, media_type, content) VALUES
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2001', 'image/jpeg', pg_read_binary_file('/tmp/hotel-exterior.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2002', 'image/jpeg', pg_read_binary_file('/tmp/room.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2003', 'image/jpeg', pg_read_binary_file('/tmp/pool.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2101', 'image/jpeg', pg_read_binary_file('/tmp/pune.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2102', 'image/jpeg', pg_read_binary_file('/tmp/hyderabad.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2103', 'image/jpeg', pg_read_binary_file('/tmp/bengaluru.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2104', 'image/jpeg', pg_read_binary_file('/tmp/delhi.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2105', 'image/jpeg', pg_read_binary_file('/tmp/mumbai.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2106', 'image/jpeg', pg_read_binary_file('/tmp/nagpur.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2107', 'image/jpeg', pg_read_binary_file('/tmp/jaipur.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2201', 'image/jpeg', pg_read_binary_file('/tmp/king.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2202', 'image/jpeg', pg_read_binary_file('/tmp/twin.jpg')),
  ('b23ab7ce-0246-4f3f-92c1-49ea58bb2203', 'image/jpeg', pg_read_binary_file('/tmp/family.jpg'))
ON CONFLICT (id) DO NOTHING;

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
WHERE owner_id = (SELECT id FROM app_user WHERE email = 'demo-catalog-owner@airstay.invalid');

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
  AND h.owner_id = (SELECT id FROM app_user WHERE email = 'demo-catalog-owner@airstay.invalid');
'@

$sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U airstay -d airstay
if ($LASTEXITCODE -ne 0) {
    throw 'Could not import the demo photos.'
}

Write-Output 'Demo photos imported and attached to the seeded catalog.'
