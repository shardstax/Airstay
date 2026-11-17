-- Proof that a booking load test did not overbook. Usage: psql -v run=<RUN_ID> -f load-tests/verify-no-overbooking.sql
-- Looks only at the hotels the run created ("Load Hotel <RUN_ID> n"). Every check must show 0 violations.
--
--  1. oversold      : nights where booked + reserved is more than the rooms that exist (the CHECK constraint also forbids this)
--  2. books_drift   : nights where the inventory counters differ from what the booking rows say. Counted from bookings:
--                     CONFIRMED -> booked; RESERVED / GUESTS_ADDED / PAYMENTS_PENDING -> reserved; CANCELLED / EXPIRED -> nothing
--  3. negative      : a counter below zero
\set pattern 'Load Hotel ':run' %'
WITH load_hotels AS (SELECT id FROM hotel WHERE name LIKE :'pattern'),
expected AS (
  SELECT i.id,
         i.booked_count, i.reserved_count, i.total_count,
         COALESCE(SUM(b.rooms_count) FILTER (WHERE b.booking_status = 'CONFIRMED'), 0) AS booked_expected,
         COALESCE(SUM(b.rooms_count) FILTER (WHERE b.booking_status IN ('RESERVED','GUESTS_ADDED','PAYMENTS_PENDING')), 0) AS reserved_expected
  FROM inventory i
  JOIN load_hotels h ON h.id = i.hotel_id
  LEFT JOIN booking b ON b.room_id = i.room_id AND i.date >= b.check_in_date AND i.date < b.check_out_date
  GROUP BY i.id
)
SELECT
  (SELECT count(*) FROM load_hotels)                                                   AS hotels,
  (SELECT count(*) FROM expected)                                                      AS inventory_nights,
  (SELECT count(*) FROM expected WHERE booked_count + reserved_count > total_count)    AS oversold,
  (SELECT count(*) FROM expected WHERE booked_count <> booked_expected
                                    OR reserved_count <> reserved_expected)            AS counters_differ_from_bookings,
  (SELECT count(*) FROM expected WHERE booked_count < 0 OR reserved_count < 0)         AS negative_counters,
  (SELECT max(booked_count + reserved_count) FROM expected)                            AS fullest_night,
  (SELECT count(*) FROM expected WHERE booked_count + reserved_count = total_count)    AS nights_sold_out;

SELECT b.booking_status, count(*) AS bookings, COALESCE(sum(b.rooms_count), 0) AS rooms
FROM booking b JOIN hotel h ON h.id = b.hotel_id
WHERE h.name LIKE :'pattern'
GROUP BY b.booking_status ORDER BY b.booking_status;
