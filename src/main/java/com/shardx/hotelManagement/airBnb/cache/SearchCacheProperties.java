package com.shardx.hotelManagement.airBnb.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Settings for the Redis search-result cache (prefix {@code airstay.search-cache}). Mutable so tests can switch it. */
@Data
@Component
@ConfigurationProperties(prefix = "airstay.search-cache")
public class SearchCacheProperties {

    /** Master switch. Off = every search goes straight to PostgreSQL. */
    private boolean enabled = true;

    /** How long a cached result may be served. This bounds how stale a search result can be. */
    private Duration ttl = Duration.ofSeconds(30);

    /** Random spread applied to the TTL (0.2 = plus or minus 20%) so popular entries do not all expire together. */
    private double ttlJitter = 0.2;
}
