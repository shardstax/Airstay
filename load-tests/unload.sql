-- Removes everything a booking load test created. Usage: psql -v run=<RUN_ID> -f load-tests/unload.sql
-- (users "load-manager-<RUN_ID>@example.test" and "load-user-<RUN_ID>-n@example.test" are removed too.)
\set pattern 'Load Hotel ':run' %'
\set manager 'load-manager-':run'@example.test'
\set users 'load-user-':run'-%@example.test'
BEGIN;
DELETE FROM booking_guest WHERE booking_id IN (SELECT b.id FROM booking b JOIN hotel h ON h.id = b.hotel_id WHERE h.name LIKE :'pattern');
DELETE FROM booking WHERE hotel_id IN (SELECT id FROM hotel WHERE name LIKE :'pattern');
DELETE FROM inventory WHERE hotel_id IN (SELECT id FROM hotel WHERE name LIKE :'pattern');
DELETE FROM hotel_min_price WHERE hotel_id IN (SELECT id FROM hotel WHERE name LIKE :'pattern');
DELETE FROM room WHERE hotel_id IN (SELECT id FROM hotel WHERE name LIKE :'pattern');
DELETE FROM hotel WHERE name LIKE :'pattern';
DELETE FROM guest WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE :'users');
DELETE FROM app_user WHERE email LIKE :'users' OR email = :'manager';
COMMIT;
