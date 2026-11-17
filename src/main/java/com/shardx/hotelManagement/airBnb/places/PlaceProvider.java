package com.shardx.hotelManagement.airBnb.places;

import java.util.List;
import java.util.Optional;

/** Where real places come from. Implementations throw {@link PlaceLookupException} when the source cannot be reached. */
public interface PlaceProvider {

    /** Places whose name starts with or matches {@code query}, most relevant first, at most {@code limit}. */
    List<PlaceDto> search(String query, int limit);

    /** One place by its provider id, or empty if there is no such place. */
    Optional<PlaceDto> findById(long id);
}
