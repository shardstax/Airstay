-- Seeds realistic search-test data straight into PostgreSQL. (Through the API this would take many minutes:
-- hotel activation saves 366 inventory rows per room one by one.)
--
-- Usage, from the repo folder with the stack running:
--   docker compose exec -T postgres psql -U airstay -d airstay -v hotels=300 -v cities=10 -v rooms=4 < load-tests/seed.sql
-- Defaults: 300 hotels in 10 cities (SeedCity-1 .. SeedCity-10), 4 room types each, one year of nightly inventory.
-- Some nights are partly or fully booked so the availability filter has real work to do.
-- Remove everything again with load-tests/unseed.sql. Seed hotels belong to a user that cannot log in.
\set ON_ERROR_STOP on
\if :{?hotels} \else \set hotels 300 \endif
\if :{?cities} \else \set cities 10 \endif
\if :{?rooms} \else \set rooms 4 \endif

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_user WHERE email = 'seed-owner@airstay.local') THEN
        RAISE EXCEPTION 'Already seeded: run load-tests/unseed.sql first';
    END IF;
END $$;

-- The password is a well-formed but unusable BCrypt string, so nobody can log in as the seed owner.
INSERT INTO app_user (email, password, name)
VALUES ('seed-owner@airstay.local', '$2a$10$' || repeat('x', 53), 'Seed Owner');
INSERT INTO app_user_roles (user_id, role)
SELECT id, 'HOTEL_MANAGER' FROM app_user WHERE email = 'seed-owner@airstay.local';

INSERT INTO hotel (name, city, photos, amenities, address, phone_number, email, location, active, owner_id,
                   created_at, updated_at)
SELECT 'Seed Hotel ' || g,
       'SeedCity-' || (1 + (g % :cities)),
       ARRAY['hotel.jpg'], ARRAY['WiFi', 'Pool'],
       'Seed Street ' || g, '+91 9000000000', 'hotel' || g || '@airstay.local', '0,0',
       true, o.id, now(), now()
FROM generate_series(1, :hotels) g
CROSS JOIN (SELECT id FROM app_user WHERE email = 'seed-owner@airstay.local') o;

INSERT INTO room (hotel_id, type, base_price, photos, amenities, total_count, capacity, created_at, updated_at)
SELECT h.id, 'Room type ' || k, 1500 + k * 700 + (h.id % 7) * 150,
       ARRAY['room.jpg'], ARRAY['AC', 'TV'], 5 + (h.id % 10), 1 + (k % 4), now(), now()
FROM hotel h
CROSS JOIN generate_series(1, :rooms) k
WHERE h.owner_id = (SELECT id FROM app_user WHERE email = 'seed-owner@airstay.local');

-- One row per room per night for today .. today + 365 (366 nights), like hotel activation creates.
INSERT INTO inventory (hotel_id, room_id, date, booked_count, reserved_count, total_count, surge_factor, price,
                       city, closed, created_at, updated_at)
SELECT r.hotel_id, r.id, d::date,
       CASE WHEN (r.id + (d::date - current_date)) % 3 = 0
            THEN (r.id * 7 + (d::date - current_date)) % (r.total_count + 1)   -- 0 .. total_count (can be sold out)
            ELSE 0 END,
       0, r.total_count, 1.00, r.base_price, h.city, false, now(), now()
FROM room r
JOIN hotel h ON h.id = r.hotel_id
CROSS JOIN generate_series(current_date, current_date + 365, interval '1 day') d
WHERE h.owner_id = (SELECT id FROM app_user WHERE email = 'seed-owner@airstay.local');

-- The "from" price per hotel per night that search reads (the pricing job normally maintains this).
INSERT INTO hotel_min_price (hotel_id, date, price, created_at, updated_at)
SELECT i.hotel_id, i.date, min(i.price), now(), now()
FROM inventory i
JOIN hotel h ON h.id = i.hotel_id
WHERE h.owner_id = (SELECT id FROM app_user WHERE email = 'seed-owner@airstay.local')
GROUP BY i.hotel_id, i.date;

COMMIT;

ANALYZE hotel;
ANALYZE room;
ANALYZE inventory;
ANALYZE hotel_min_price;

SELECT (SELECT count(*) FROM hotel WHERE email LIKE '%@airstay.local')            AS seed_hotels,
       (SELECT count(*) FROM room r JOIN hotel h ON h.id = r.hotel_id
         WHERE h.email LIKE '%@airstay.local')                                    AS seed_rooms,
       (SELECT count(*) FROM inventory i JOIN hotel h ON h.id = i.hotel_id
         WHERE h.email LIKE '%@airstay.local')                                    AS seed_inventory_rows,
       (SELECT count(*) FROM hotel_min_price m JOIN hotel h ON h.id = m.hotel_id
         WHERE h.email LIKE '%@airstay.local')                                    AS seed_min_price_rows;
