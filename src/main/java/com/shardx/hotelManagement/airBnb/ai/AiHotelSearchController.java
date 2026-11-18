package com.shardx.hotelManagement.airBnb.ai;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimiter;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated while the assistant is introduced: this prevents an unauthenticated endpoint from spending model
 * budget. A later guest-facing release must add an explicit IP quota before opening it publicly.
 */
@RestController
@RequestMapping("/ai/hotel-search")
@RequiredArgsConstructor
public class AiHotelSearchController {

    private final HotelSearchIntentExtractor extractor;
    private final HotelSearchIntentSanitizer sanitizer;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;

    @PostMapping("/interpret")
    @Operation(summary = "Interpret a natural-language hotel request into safe search filters", tags = {"AI Search"})
    public ResponseEntity<AiHotelSearchInterpretation> interpret(@Valid @RequestBody AiHotelSearchRequest request,
                                                                   HttpServletRequest httpRequest) {
        rateLimiter.check("ai-search-ip", httpRequest.getRemoteAddr(), rateLimits.getAiSearchPerIp());
        return ResponseEntity.ok(sanitizer.sanitize(extractor.extract(request.message())));
    }
}
