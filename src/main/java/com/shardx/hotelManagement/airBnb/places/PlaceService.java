package com.shardx.hotelManagement.airBnb.places;

import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * Place search and lookup in front of the provider, with a cache. Searches while someone types would otherwise hit the
 * provider on every keystroke; place data hardly changes, so a day-long cache is safe, and a place chosen from a search is
 * remembered so saving a hotel right after needs no second call.
 */
@Service
public class PlaceService {

    private final PlaceProvider provider;
    private final PlaceProperties properties;
    private final ExpiringCache<String, List<PlaceDto>> searches;
    private final ExpiringCache<Long, PlaceDto> places;

    public PlaceService(PlaceProvider provider, PlaceProperties properties, Clock clock) {
        this.provider = provider;
        this.properties = properties;
        this.searches = new ExpiringCache<>(properties.getCacheSize(), properties.getCacheTtl(), clock);
        this.places = new ExpiringCache<>(properties.getCacheSize(), properties.getCacheTtl(), clock);
    }

    /** Suggestions for what a user has typed so far. Not cached when empty, so a provider hiccup is not remembered. */
    public List<PlaceDto> search(String query) {
        String normalised = normalise(query);
        String key = normalised.toLowerCase(Locale.ROOT);

        List<PlaceDto> cached = searches.get(key).orElse(null);
        if (cached != null) {
            return cached;
        }
        List<PlaceDto> found = List.copyOf(provider.search(normalised, properties.getMaxResults()));
        if (!found.isEmpty()) {
            searches.put(key, found);
            found.forEach(place -> places.put(place.getId(), place));
        }
        return found;
    }

    /** The place with this id, or a 400: managers may only use places that really exist. */
    public PlaceDto require(long id) {
        PlaceDto cached = places.get(id).orElse(null);
        if (cached != null) {
            return cached;
        }
        PlaceDto place = provider.findById(id)
                .orElseThrow(() -> new BadRequestException("Unknown place: choose a city from the suggestions"));
        places.put(id, place);
        return place;
    }

    /** Collapses runs of white space, so "  New   York " and "New York" are the same search. */
    static String normalise(String query) {
        return query.trim().replaceAll("\\s+", " ");
    }
}
