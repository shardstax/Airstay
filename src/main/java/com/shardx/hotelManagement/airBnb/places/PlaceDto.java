package com.shardx.hotelManagement.airBnb.places;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A real place (city, town or village) from the geocoding provider. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaceDto {

    /** The provider's stable id for this place (a GeoNames id). */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** State, province or similar; absent for city-states and small countries. */
    private String region;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String country;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String countryCode;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Double latitude;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Double longitude;

    /** How the place is written everywhere in AirStay, e.g. "Pune, Maharashtra, India". Search matches on this text. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    /** Builds the label from its parts, leaving the region out when it only repeats the name. */
    public static String labelFor(String name, String region, String country) {
        StringBuilder label = new StringBuilder(name);
        if (region != null && !region.isBlank() && !region.equalsIgnoreCase(name)) {
            label.append(", ").append(region);
        }
        return label.append(", ").append(country).toString();
    }
}
