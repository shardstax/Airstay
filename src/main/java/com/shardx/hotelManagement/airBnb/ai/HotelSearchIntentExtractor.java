package com.shardx.hotelManagement.airBnb.ai;

/** Provider boundary: swapping model vendors must not change hotel-search business rules. */
public interface HotelSearchIntentExtractor {
    AiHotelSearchIntent extract(AiHotelSearchRequest request);
}
