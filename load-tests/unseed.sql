-- Removes everything created by load-tests/seed.sql (and only that).
--   docker compose exec -T postgres psql -U airstay -d airstay < load-tests/unseed.sql
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE seed_hotels AS
SELECT h.id FROM hotel h JOIN app_user u ON u.id = h.owner_id WHERE u.email = 'seed-owner@airstay.local';

DELETE FROM hotel_min_price WHERE hotel_id IN (SELECT id FROM seed_hotels);
DELETE FROM inventory WHERE hotel_id IN (SELECT id FROM seed_hotels);
DELETE FROM room WHERE hotel_id IN (SELECT id FROM seed_hotels);
DELETE FROM hotel WHERE id IN (SELECT id FROM seed_hotels);
DELETE FROM app_user_roles WHERE user_id IN (SELECT id FROM app_user WHERE email = 'seed-owner@airstay.local');
DELETE FROM app_user WHERE email = 'seed-owner@airstay.local';

COMMIT;
ANALYZE inventory;
ANALYZE hotel_min_price;
