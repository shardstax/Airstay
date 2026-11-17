package com.shardx.hotelManagement.airBnb.catalog;

import java.util.List;

/**
 * The fixed choices a hotel manager picks from, instead of typing free text. One list, used by the API's validation, by
 * {@code GET /catalog}, and so by the web app's drop-downs: they cannot disagree.
 *
 * <p>Values are matched exactly (case included). To add a choice, add it here.
 */
public final class Catalog {

    public static final List<String> HOTEL_AMENITIES = List.of(
            "Air conditioning", "Airport shuttle", "Bar", "Beach access", "Breakfast", "Business centre", "Gym",
            "Laundry", "Parking", "Pet friendly", "Pool", "Restaurant", "Room service", "Spa", "WiFi");

    public static final List<String> ROOM_AMENITIES = List.of(
            "Air conditioning", "Balcony", "Bathtub", "City view", "Kitchenette", "Mini bar", "Safe", "Sea view", "TV",
            "Work desk");

    public static final List<String> ROOM_TYPES = List.of(
            "Standard", "Deluxe", "Superior", "Suite", "Family", "Studio", "Villa", "Dormitory");

    private Catalog() {
    }

    /** The kinds of list a field can be checked against. */
    public enum Kind {
        HOTEL_AMENITY(HOTEL_AMENITIES),
        ROOM_AMENITY(ROOM_AMENITIES),
        ROOM_TYPE(ROOM_TYPES);

        private final List<String> choices;

        Kind(List<String> values) {
            this.choices = values;
        }

        public List<String> choices() {
            return choices;
        }
    }
}
