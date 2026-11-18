-- AirStay demo catalog for local development and portfolio demonstrations.
--
-- This is intentionally NOT a Flyway migration: it creates fictional hotels and must never be
-- applied automatically to a production database. It is safe to run more than once. The script
-- seeds 21 hotels, 42 room types, and availability from today through the next year.
--
-- Run against the local Compose database:
--   Get-Content scripts/seed-demo-catalog.sql | docker compose exec -T postgres psql -U airstay -d airstay

-- This local-only owner can manage the seeded hotels and upload photos through the UI.
-- Never run this script against production: the credentials below are intentionally public in the repository.
INSERT INTO app_user (email, password, name)
VALUES ('demo-catalog-owner@airstay.invalid',
        '$2a$10$f6o2R6kw1AfMmDi1NXpv5OUM9dj3yLRidiWTo8/dGsOX5Tpfbx/Du',
        'AirStay Demo Catalog')
ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, name = EXCLUDED.name;

INSERT INTO app_user_roles (user_id, role)
SELECT id, role
FROM app_user
CROSS JOIN (VALUES ('GUEST'::VARCHAR), ('HOTEL_MANAGER'::VARCHAR)) AS required_roles(role)
WHERE email = 'demo-catalog-owner@airstay.invalid'
ON CONFLICT (user_id, role) DO NOTHING;

WITH demo_hotels (name, city, place_id, address, phone_number, email, location, amenities) AS (
    VALUES
        ('The Deccan Courtyard', 'Pune, Maharashtra, India', 1259229, 'FC Road, Shivajinagar, Pune', '+91 20 4100 1200', 'pune@demo.airstay.invalid', 'Shivajinagar', ARRAY['Free WiFi', 'Breakfast', 'Parking', 'Gym']::TEXT[]),
        ('Koregaon Park House', 'Pune, Maharashtra, India', 1259229, 'North Main Road, Koregaon Park, Pune', '+91 20 4100 1210', 'koregaonpark@demo.airstay.invalid', 'Koregaon Park', ARRAY['Free WiFi', 'Cafe', 'Parking', 'Pet friendly']::TEXT[]),
        ('Hinjawadi Business Stay', 'Pune, Maharashtra, India', 1259229, 'Phase 1, Hinjawadi, Pune', '+91 20 4100 1220', 'hinjawadi@demo.airstay.invalid', 'Hinjawadi', ARRAY['Free WiFi', 'Workspace', 'Gym', 'Airport shuttle']::TEXT[]),
        ('Pearl Lake Retreat', 'Hyderabad, Telangana, India', 1269843, 'Road No. 36, Jubilee Hills, Hyderabad', '+91 40 4100 2200', 'hyderabad@demo.airstay.invalid', 'Jubilee Hills', ARRAY['Free WiFi', 'Pool', 'Restaurant', 'Parking']::TEXT[]),
        ('Banjara Hills Grand', 'Hyderabad, Telangana, India', 1269843, 'Road No. 12, Banjara Hills, Hyderabad', '+91 40 4100 2210', 'banjarahills@demo.airstay.invalid', 'Banjara Hills', ARRAY['Free WiFi', 'Spa', 'Breakfast', 'Valet parking']::TEXT[]),
        ('HITEC City Connect', 'Hyderabad, Telangana, India', 1269843, 'Madhapur Main Road, Hyderabad', '+91 40 4100 2220', 'hiteccity@demo.airstay.invalid', 'Madhapur', ARRAY['Free WiFi', 'Workspace', 'Gym', 'Restaurant']::TEXT[]),
        ('Garden City Suites', 'Bengaluru, Karnataka, India', 1277333, '100 Feet Road, Indiranagar, Bengaluru', '+91 80 4100 3200', 'bengaluru@demo.airstay.invalid', 'Indiranagar', ARRAY['Free WiFi', 'Workspace', 'Breakfast', 'Airport shuttle']::TEXT[]),
        ('Whitefield Park Inn', 'Bengaluru, Karnataka, India', 1277333, 'ITPL Main Road, Whitefield, Bengaluru', '+91 80 4100 3210', 'whitefield@demo.airstay.invalid', 'Whitefield', ARRAY['Free WiFi', 'Pool', 'Workspace', 'Parking']::TEXT[]),
        ('Koramangala House', 'Bengaluru, Karnataka, India', 1277333, '80 Feet Road, Koramangala, Bengaluru', '+91 80 4100 3220', 'koramangala@demo.airstay.invalid', 'Koramangala', ARRAY['Free WiFi', 'Cafe', 'Breakfast', 'Laundry']::TEXT[]),
        ('Capital House', 'Delhi, National Capital Territory of Delhi, India', 1273294, 'Connaught Place, New Delhi', '+91 11 4100 4200', 'delhi@demo.airstay.invalid', 'Connaught Place', ARRAY['Free WiFi', 'Restaurant', 'Gym', 'Airport shuttle']::TEXT[]),
        ('Aerocity Grand', 'Delhi, National Capital Territory of Delhi, India', 1273294, 'Aerocity, New Delhi', '+91 11 4100 4210', 'aerocity@demo.airstay.invalid', 'Aerocity', ARRAY['Free WiFi', 'Airport shuttle', 'Pool', 'Gym']::TEXT[]),
        ('Hauz Khas Retreat', 'Delhi, National Capital Territory of Delhi, India', 1273294, 'Hauz Khas Village, New Delhi', '+91 11 4100 4220', 'hauzkhas@demo.airstay.invalid', 'Hauz Khas', ARRAY['Free WiFi', 'Garden', 'Breakfast', 'Restaurant']::TEXT[]),
        ('Harbour View Residency', 'Mumbai, Maharashtra, India', 1275339, 'Colaba Causeway, Mumbai', '+91 22 4100 5200', 'mumbai@demo.airstay.invalid', 'Colaba', ARRAY['Free WiFi', 'Sea view', 'Breakfast', 'Concierge']::TEXT[]),
        ('Bandra Bay House', 'Mumbai, Maharashtra, India', 1275339, 'Carter Road, Bandra West, Mumbai', '+91 22 4100 5210', 'bandra@demo.airstay.invalid', 'Bandra West', ARRAY['Free WiFi', 'Sea view', 'Cafe', 'Airport shuttle']::TEXT[]),
        ('Andheri Airport Inn', 'Mumbai, Maharashtra, India', 1275339, 'Marol, Andheri East, Mumbai', '+91 22 4100 5220', 'andheri@demo.airstay.invalid', 'Andheri East', ARRAY['Free WiFi', 'Airport shuttle', 'Breakfast', 'Workspace']::TEXT[]),
        ('Orange City Inn', 'Nagpur, Maharashtra, India', 1262180, 'Ramdaspeth, Nagpur', '+91 712 410 6200', 'nagpur@demo.airstay.invalid', 'Ramdaspeth', ARRAY['Free WiFi', 'Parking', 'Restaurant', 'Family rooms']::TEXT[]),
        ('Civil Lines Lodge', 'Nagpur, Maharashtra, India', 1262180, 'Civil Lines, Nagpur', '+91 712 410 6210', 'civillines@demo.airstay.invalid', 'Civil Lines', ARRAY['Free WiFi', 'Breakfast', 'Garden', 'Parking']::TEXT[]),
        ('Airport Grove Nagpur', 'Nagpur, Maharashtra, India', 1262180, 'Wardha Road, Nagpur', '+91 712 410 6220', 'airportgrove@demo.airstay.invalid', 'Wardha Road', ARRAY['Free WiFi', 'Airport shuttle', 'Pool', 'Restaurant']::TEXT[]),
        ('Pink City Haveli', 'Jaipur, Rajasthan, India', 1269515, 'MI Road, Jaipur', '+91 141 410 7200', 'jaipur@demo.airstay.invalid', 'MI Road', ARRAY['Free WiFi', 'Rooftop terrace', 'Breakfast', 'Parking']::TEXT[])
        ,('Amer Fort Residency', 'Jaipur, Rajasthan, India', 1269515, 'Amer Road, Jaipur', '+91 141 410 7210', 'amer@demo.airstay.invalid', 'Amer', ARRAY['Free WiFi', 'Pool', 'Courtyard', 'Restaurant']::TEXT[])
        ,('Malviya Nagar Suites', 'Jaipur, Rajasthan, India', 1269515, 'Malviya Nagar, Jaipur', '+91 141 410 7220', 'malviyanagar@demo.airstay.invalid', 'Malviya Nagar', ARRAY['Free WiFi', 'Workspace', 'Breakfast', 'Parking']::TEXT[])
)
INSERT INTO hotel (name, city, place_id, photos, amenities, address, phone_number, email, location, active, owner_id, created_at, updated_at)
SELECT d.name, d.city, d.place_id, ARRAY[]::TEXT[], d.amenities, d.address, d.phone_number, d.email, d.location,
       true, u.id, now(), now()
FROM demo_hotels d
CROSS JOIN (SELECT id FROM app_user WHERE email = 'demo-catalog-owner@airstay.invalid') u
WHERE NOT EXISTS (SELECT 1 FROM hotel h WHERE h.name = d.name);

WITH demo_rooms (hotel_name, type, base_price, amenities, total_count, capacity) AS (
    VALUES
        ('The Deccan Courtyard', 'Deluxe King', 4200.00, ARRAY['King bed', 'City view', 'Air conditioning']::TEXT[], 8, 2),
        ('The Deccan Courtyard', 'Family Studio', 6100.00, ARRAY['Two queen beds', 'Kitchenette', 'Air conditioning']::TEXT[], 4, 4),
        ('Koregaon Park House', 'Garden King', 4700.00, ARRAY['King bed', 'Garden view', 'Air conditioning']::TEXT[], 7, 2),
        ('Koregaon Park House', 'Loft Suite', 6900.00, ARRAY['King bed', 'Living room', 'Kitchenette']::TEXT[], 3, 3),
        ('Hinjawadi Business Stay', 'Executive King', 3900.00, ARRAY['King bed', 'Desk', 'Smart TV']::TEXT[], 10, 2),
        ('Hinjawadi Business Stay', 'Business Twin', 3700.00, ARRAY['Two twin beds', 'Desk', 'Blackout curtains']::TEXT[], 8, 2),
        ('Pearl Lake Retreat', 'Lake View King', 5200.00, ARRAY['King bed', 'Lake view', 'Balcony']::TEXT[], 7, 2),
        ('Pearl Lake Retreat', 'Executive Twin', 4800.00, ARRAY['Two twin beds', 'Workspace', 'Air conditioning']::TEXT[], 6, 2),
        ('Banjara Hills Grand', 'Premier King', 5800.00, ARRAY['King bed', 'Rain shower', 'City view']::TEXT[], 8, 2),
        ('Banjara Hills Grand', 'Club Suite', 8500.00, ARRAY['King bed', 'Lounge access', 'Sitting area']::TEXT[], 4, 3),
        ('HITEC City Connect', 'Tech King', 4500.00, ARRAY['King bed', 'Ergonomic desk', 'Fast WiFi']::TEXT[], 12, 2),
        ('HITEC City Connect', 'Apartment Suite', 7100.00, ARRAY['King bed', 'Kitchenette', 'Washer dryer']::TEXT[], 4, 3),
        ('Garden City Suites', 'Urban King', 5600.00, ARRAY['King bed', 'Workspace', 'Smart TV']::TEXT[], 8, 2),
        ('Garden City Suites', 'Two Bedroom Suite', 8900.00, ARRAY['Two bedrooms', 'Living room', 'Kitchenette']::TEXT[], 3, 5),
        ('Whitefield Park Inn', 'Park King', 4900.00, ARRAY['King bed', 'Park view', 'Smart TV']::TEXT[], 9, 2),
        ('Whitefield Park Inn', 'Family Twin', 6500.00, ARRAY['Two queen beds', 'Sofa bed', 'Air conditioning']::TEXT[], 5, 4),
        ('Koramangala House', 'Courtyard King', 5100.00, ARRAY['King bed', 'Courtyard view', 'Smart TV']::TEXT[], 7, 2),
        ('Koramangala House', 'Neighbourhood Studio', 6000.00, ARRAY['Queen bed', 'Kitchenette', 'Workspace']::TEXT[], 5, 2),
        ('Capital House', 'Premier King', 6800.00, ARRAY['King bed', 'Workspace', 'Rain shower']::TEXT[], 7, 2),
        ('Capital House', 'Club Twin', 6300.00, ARRAY['Two twin beds', 'Club lounge access', 'Smart TV']::TEXT[], 6, 2),
        ('Aerocity Grand', 'Runway King', 7300.00, ARRAY['King bed', 'Soundproofing', 'Airport view']::TEXT[], 10, 2),
        ('Aerocity Grand', 'Transit Twin', 6100.00, ARRAY['Two twin beds', 'Blackout curtains', 'Desk']::TEXT[], 9, 2),
        ('Hauz Khas Retreat', 'Garden King', 5400.00, ARRAY['King bed', 'Garden view', 'Reading nook']::TEXT[], 6, 2),
        ('Hauz Khas Retreat', 'Heritage Suite', 8200.00, ARRAY['King bed', 'Sitting area', 'Courtyard view']::TEXT[], 3, 3),
        ('Harbour View Residency', 'Sea View King', 7900.00, ARRAY['King bed', 'Sea view', 'Balcony']::TEXT[], 6, 2),
        ('Harbour View Residency', 'Family Suite', 11200.00, ARRAY['Two bedrooms', 'Sea view', 'Living room']::TEXT[], 3, 5),
        ('Bandra Bay House', 'Bay King', 7600.00, ARRAY['King bed', 'Bay view', 'Balcony']::TEXT[], 6, 2),
        ('Bandra Bay House', 'Artist Loft', 9800.00, ARRAY['King bed', 'Living room', 'Kitchenette']::TEXT[], 3, 3),
        ('Andheri Airport Inn', 'Airport King', 4800.00, ARRAY['King bed', 'Soundproofing', 'Desk']::TEXT[], 12, 2),
        ('Andheri Airport Inn', 'Family Connector', 7200.00, ARRAY['Two queen beds', 'Connecting rooms', 'Smart TV']::TEXT[], 5, 4),
        ('Orange City Inn', 'Comfort King', 3300.00, ARRAY['King bed', 'Workspace', 'Air conditioning']::TEXT[], 10, 2),
        ('Orange City Inn', 'Garden Family Room', 4900.00, ARRAY['Two queen beds', 'Garden view', 'Air conditioning']::TEXT[], 5, 4),
        ('Civil Lines Lodge', 'Classic King', 3500.00, ARRAY['King bed', 'Reading chair', 'Air conditioning']::TEXT[], 8, 2),
        ('Civil Lines Lodge', 'Verandah Suite', 5700.00, ARRAY['King bed', 'Private verandah', 'Sitting area']::TEXT[], 4, 3),
        ('Airport Grove Nagpur', 'Grove King', 4100.00, ARRAY['King bed', 'Garden view', 'Smart TV']::TEXT[], 9, 2),
        ('Airport Grove Nagpur', 'Poolside Family Room', 5900.00, ARRAY['Two queen beds', 'Pool access', 'Air conditioning']::TEXT[], 5, 4),
        ('Pink City Haveli', 'Heritage King', 4600.00, ARRAY['King bed', 'Courtyard view', 'Air conditioning']::TEXT[], 8, 2),
        ('Pink City Haveli', 'Terrace Suite', 7200.00, ARRAY['King bed', 'Private terrace', 'Sitting area']::TEXT[], 4, 3),
        ('Amer Fort Residency', 'Fort View King', 5300.00, ARRAY['King bed', 'Fort view', 'Balcony']::TEXT[], 7, 2),
        ('Amer Fort Residency', 'Courtyard Family Suite', 8100.00, ARRAY['Two bedrooms', 'Courtyard view', 'Living room']::TEXT[], 3, 5),
        ('Malviya Nagar Suites', 'Modern King', 4000.00, ARRAY['King bed', 'Workspace', 'Smart TV']::TEXT[], 9, 2),
        ('Malviya Nagar Suites', 'City Family Studio', 6200.00, ARRAY['Two queen beds', 'Kitchenette', 'Air conditioning']::TEXT[], 5, 4)
)
INSERT INTO room (hotel_id, type, base_price, photos, amenities, total_count, capacity, created_at, updated_at)
SELECT h.id, d.type, d.base_price, ARRAY[]::TEXT[], d.amenities, d.total_count, d.capacity, now(), now()
FROM demo_rooms d
JOIN hotel h ON h.name = d.hotel_name
WHERE NOT EXISTS (SELECT 1 FROM room r WHERE r.hotel_id = h.id AND r.type = d.type);

-- Hotel search only returns properties with inventory for the whole requested stay. Re-running this
-- block adds future nights without duplicates.
INSERT INTO inventory (hotel_id, room_id, date, booked_count, reserved_count, total_count, surge_factor,
                       price, city, closed, created_at, updated_at)
SELECT h.id, r.id, CURRENT_DATE + nights.day, 0, 0, r.total_count, 1.00, r.base_price, h.city, false, now(), now()
FROM hotel h
JOIN room r ON r.hotel_id = h.id
CROSS JOIN generate_series(0, 365) AS nights(day)
WHERE h.owner_id = (SELECT id FROM app_user WHERE email = 'demo-catalog-owner@airstay.invalid')
ON CONFLICT (hotel_id, room_id, date) DO NOTHING;

-- Search also needs this denormalized per-hotel "from" price.
INSERT INTO hotel_min_price (hotel_id, date, price, created_at, updated_at)
SELECT h.id, CURRENT_DATE + nights.day, MIN(r.base_price), now(), now()
FROM hotel h
JOIN room r ON r.hotel_id = h.id
CROSS JOIN generate_series(0, 365) AS nights(day)
WHERE h.owner_id = (SELECT id FROM app_user WHERE email = 'demo-catalog-owner@airstay.invalid')
GROUP BY h.id, nights.day
ON CONFLICT (hotel_id, date) DO UPDATE
SET price = EXCLUDED.price, updated_at = EXCLUDED.updated_at;
