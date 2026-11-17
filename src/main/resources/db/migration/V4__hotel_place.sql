-- A hotel's city is now chosen from real places (a geocoding provider) instead of typed in. place_id is the provider's
-- stable id for the place (a GeoNames id); hotel.city and inventory.city keep holding the place's display label
-- ("Pune, Maharashtra, India"), which is what search matches on.
--
-- Nullable on purpose: hotels created before this change have no place until their owner edits them and picks one.
ALTER TABLE hotel ADD COLUMN place_id BIGINT;
CREATE INDEX idx_hotel_place ON hotel (place_id);
