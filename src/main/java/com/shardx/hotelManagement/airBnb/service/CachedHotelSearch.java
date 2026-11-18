package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.cache.CachedSearchPage;
import com.shardx.hotelManagement.airBnb.cache.SearchCache;
import com.shardx.hotelManagement.airBnb.dto.HotelPriceResponseDto;
import com.shardx.hotelManagement.airBnb.dto.HotelSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Hotel search with the Redis cache in front of {@link InventoryService#searchHotels}.
 *
 * <p>Deliberately NOT transactional: the cache lookup happens before any database transaction starts, so a cache hit
 * does not even borrow a database connection. On a miss the loader calls the transactional service method.
 */
@Service
@RequiredArgsConstructor
public class CachedHotelSearch {

    private final InventoryService inventoryService;
    private final SearchCache searchCache;

    public Page<HotelPriceResponseDto> search(HotelSearchRequest request) {
        CachedSearchPage cached = searchCache.get(key(request), () -> {
            Page<HotelPriceResponseDto> page = inventoryService.searchHotels(request);
            return new CachedSearchPage(page.getContent(), page.getTotalElements());
        });
        return new PageImpl<>(cached.content(), PageRequest.of(request.getPage(), request.getSize()),
                cached.totalElements());
    }

    /** Everything that changes the result is in the key. The city is matched exactly, like the database does. */
    static String key(HotelSearchRequest request) {
        return URLEncoder.encode(request.getCity().trim(), StandardCharsets.UTF_8)
                + ":" + request.getStartDate()
                + ":" + request.getEndDate()
                + ":" + request.getRoomsCount()
                + ":" + request.getPage()
                + ":" + request.getSize()
                + ":" + String.join(",", request.getAmenities().stream().distinct().sorted().toList());
    }
}
