package com.shardx.hotelManagement.airBnb.places;

import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimitProperties;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;

    private final PlaceService placeService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;

    @GetMapping
    @Operation(summary = "Search real places (cities, towns, villages) by name, for the search box and the hotel form",
            tags = {"Places"})
    public ResponseEntity<List<PlaceDto>> searchPlaces(@RequestParam("query") String query, HttpServletRequest request) {
        String trimmed = query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH || trimmed.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("Type between " + MIN_QUERY_LENGTH + " and " + MAX_QUERY_LENGTH + " characters");
        }
        // Public, and called on every pause while typing: limited per address so it cannot be used to hammer the provider.
        rateLimiter.check("places-ip", request.getRemoteAddr(), rateLimits.getPlacesPerIp());
        return ResponseEntity.ok(placeService.search(trimmed));
    }
}
