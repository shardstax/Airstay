package com.shardx.hotelManagement.airBnb.cache;

import com.shardx.hotelManagement.airBnb.dto.HotelPriceResponseDto;

import java.util.List;

/**
 * What the search cache stores: plain DTOs and a total, serialised as JSON. Never entities and never Spring's
 * {@code Page}, whose JSON form is not stable.
 */
public record CachedSearchPage(List<HotelPriceResponseDto> content, long totalElements) {
}
