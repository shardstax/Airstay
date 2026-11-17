-- Removes the hotels created by load-tests/activation.js (names start with "Bench Hotel").
-- They never have bookings. Seeded data is removed by unseed.sql instead.
--   docker compose exec -T postgres psql -U airstay -d airstay < load-tests/unbench.sql
BEGIN;
DELETE FROM inventory       WHERE hotel_id IN (SELECT id FROM hotel WHERE name LIKE 'Bench Hotel %');
DELETE FROM hotel_min_price WHERE hotel_id IN (SELECT id FROM hotel WHERE name LIKE 'Bench Hotel %');
DELETE FROM room            WHERE hotel_id IN (SELECT id FROM hotel WHERE name LIKE 'Bench Hotel %');
DELETE FROM hotel           WHERE name LIKE 'Bench Hotel %';
COMMIT;
