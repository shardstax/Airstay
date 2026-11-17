package com.shardx.hotelManagement.airBnb.places;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Invents a place for any id, so tests and load tests can create hotels in as many distinct "cities" as they like without
 * calling the Internet. Enabled ONLY with {@code airstay.places.provider=synthetic}.
 *
 * <p><b>It must never run in production:</b> the application refuses to start with this and the {@code prod} profile.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "airstay.places.provider", havingValue = "synthetic")
public class SyntheticPlaceProvider implements PlaceProvider {

    /** Ids of searched places start here, well away from the ids a test picks at random for itself. */
    static final long SYNTHETIC_ID_BASE = 2_000_000_000_000L;

    public SyntheticPlaceProvider(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "airstay.places.provider=synthetic must never be used with the prod profile: it accepts any place");
        }
        log.warn("SYNTHETIC PLACES ARE ENABLED: any place id is accepted. Never use in production.");
    }

    /**
     * One place named after whatever was typed, with an id that depends only on the text, so a browser test can search for
     * a name nobody else uses, pick it from the list and get a city of its own.
     */
    @Override
    public List<PlaceDto> search(String query, int limit) {
        String name = query.trim();
        long id = SYNTHETIC_ID_BASE + Math.abs((long) name.toLowerCase(Locale.ROOT).hashCode());
        return List.of(new PlaceDto(id, name, "Test Region", "Testland", "TL", 0.0, 0.0,
                PlaceDto.labelFor(name, "Test Region", "Testland")));
    }

    @Override
    public Optional<PlaceDto> findById(long id) {
        String name = "Place " + id;
        return Optional.of(new PlaceDto(id, name, "Test Region", "Testland", "TL", 0.0, 0.0,
                PlaceDto.labelFor(name, "Test Region", "Testland")));
    }
}
