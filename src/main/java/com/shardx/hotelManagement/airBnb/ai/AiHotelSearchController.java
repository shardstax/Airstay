package com.shardx.hotelManagement.airBnb.ai;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimiter;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, rate-limited interpretation of bounded hotel search conversations. */
@RestController
@RequestMapping("/ai/hotel-search")
@RequiredArgsConstructor
public class AiHotelSearchController {

    private final HotelSearchIntentExtractor extractor;
    private final HotelSearchIntentSanitizer sanitizer;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;
    private final AiSearchSettings settings;
    private final AiSearchProperties properties;

    @GetMapping("/status")
    public ResponseEntity<AiSearchStatus> getAiSearchStatus() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AiSearchStatus(settings.isAvailable()));
    }

    @PostMapping("/interpret")
    @Operation(summary = "Interpret a natural-language hotel request into safe search filters", tags = {"AI Search"})
    public ResponseEntity<AiHotelSearchInterpretation> interpret(@Valid @RequestBody AiHotelSearchRequest request,
                                                                   HttpServletRequest httpRequest) {
        if (!settings.isAvailable()) {
            throw new AiSearchUnavailableException("The AI hotel-search assistant is turned off or unavailable");
        }
        String address = httpRequest.getRemoteAddr();
        if (properties.isRequireQuota()) {
            if (!rateLimiter.checkRequired("ai-search-ip", address, rateLimits.getAiSearchPerIp())
                    || !rateLimiter.checkRequired("ai-search-global", "all", rateLimits.getAiSearchGlobal())) {
                throw new AiSearchUnavailableException("The AI assistant is paused until its usage limiter is available");
            }
        } else {
            rateLimiter.check("ai-search-ip", address, rateLimits.getAiSearchPerIp());
        }
        return ResponseEntity.ok(sanitizer.sanitize(extractor.extract(request)));
    }

    public record AiSearchStatus(boolean available) {}
}
