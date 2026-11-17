package com.shardx.hotelManagement.airBnb.places;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Settings for place lookups (prefix {@code airstay.places}). */
@Data
@Component
@ConfigurationProperties(prefix = "airstay.places")
public class PlaceProperties {

    /** {@code open-meteo} (the real provider) or {@code synthetic} (tests and load tests only; refused with the prod profile). */
    private String provider = "open-meteo";

    /** Base address of the Open-Meteo geocoding API. */
    private String baseUrl = "https://geocoding-api.open-meteo.com";

    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);

    /** How long a looked-up place or search is remembered. Place data barely changes, so this is long. */
    private Duration cacheTtl = Duration.ofHours(24);

    /** The most entries kept (searches and places together); the least recently used go first. */
    private int cacheSize = 2000;

    /** Most suggestions returned for one search. */
    private int maxResults = 8;
}
