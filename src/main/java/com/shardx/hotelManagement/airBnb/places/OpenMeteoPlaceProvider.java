package com.shardx.hotelManagement.airBnb.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Real places from Open-Meteo's free geocoding API (https://open-meteo.com/en/docs/geocoding-api): no API key, data from
 * GeoNames (CC BY 4.0, so attribution is required and shown in the web app). Free for non-commercial use; a commercial
 * deployment needs a paid plan or its own copy of the data.
 *
 * <p>Only populated places are returned (cities, towns, villages: GeoNames feature codes starting with PPL), not regions,
 * countries or landmarks.
 */
@Component
@ConditionalOnProperty(name = "airstay.places.provider", havingValue = "open-meteo", matchIfMissing = true)
public class OpenMeteoPlaceProvider implements PlaceProvider {

    private static final String POPULATED_PLACE_PREFIX = "PPL";
    private static final Map<String, String> CITY_ALIASES = Map.of(
            "allahabad", "Prayagraj",
            "bangalore", "Bengaluru",
            "bombay", "Mumbai",
            "calcutta", "Kolkata",
            "gurgaon", "Gurugram",
            "madras", "Chennai",
            "panaji", "Panjim",
            "trivandrum", "Thiruvananthapuram");

    private final RestClient client;

    @Autowired
    public OpenMeteoPlaceProvider(PlaceProperties properties) {
        // Short timeouts: a slow provider must make the search box fail fast, not hang the page.
        this(RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(properties.getConnectTimeout())
                        .withReadTimeout(properties.getReadTimeout()))), properties);
    }

    /** Takes a ready builder so a test can attach a mock server to it (which supplies its own request factory). */
    OpenMeteoPlaceProvider(RestClient.Builder builder, PlaceProperties properties) {
        this.client = builder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public List<PlaceDto> search(String query, int limit) {
        try {
            SearchResponse response = client.get()
                    .uri(uri -> uri.path("/v1/search")
                            .queryParam("name", resolveAlias(query))
                            // Ask for more than needed: non-city results are dropped below.
                            .queryParam("count", Math.min(100, limit * 3))
                            .queryParam("language", "en")
                            .queryParam("countryCode", "IN")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);
            if (response == null || response.results() == null) {
                return List.of();
            }
            Set<String> labels = new HashSet<>();
            return response.results().stream()
                    .filter(OpenMeteoPlaceProvider::isUsablePopulatedPlace)
                    .filter(result -> "IN".equals(result.countryCode()) && result.admin1() != null
                            && !result.admin1().isBlank())
                    .map(OpenMeteoPlaceProvider::toPlace)
                    .filter(place -> labels.add(place.getLabel()))
                    .limit(limit)
                    .toList();
        } catch (RestClientException e) {
            throw new PlaceLookupException("Place search is temporarily unavailable", e);
        }
    }

    private static String resolveAlias(String query) {
        int qualifier = query.indexOf(',');
        String name = qualifier < 0 ? query : query.substring(0, qualifier).stripTrailing();
        String canonical = CITY_ALIASES.get(name.toLowerCase(Locale.ROOT));
        if (canonical == null) {
            return query;
        }
        return qualifier < 0 ? canonical : canonical + query.substring(qualifier);
    }

    @Override
    public Optional<PlaceDto> findById(long id) {
        try {
            Result result = client.get()
                    .uri(uri -> uri.path("/v1/get").queryParam("id", id).queryParam("language", "en").build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> { /* "no such place" is an answer */ })
                    .body(Result.class);
            return Optional.ofNullable(result)
                    .filter(OpenMeteoPlaceProvider::isUsablePopulatedPlace)
                    .map(OpenMeteoPlaceProvider::toPlace);
        } catch (RestClientException e) {
            throw new PlaceLookupException("Place lookup is temporarily unavailable", e);
        }
    }

    private static boolean isUsablePopulatedPlace(Result result) {
        return result.id() != null && result.name() != null && result.country() != null
                && result.countryCode() != null && result.latitude() != null && result.longitude() != null
                && result.featureCode() != null && result.featureCode().startsWith(POPULATED_PLACE_PREFIX);
    }

    private static PlaceDto toPlace(Result r) {
        return new PlaceDto(r.id(), r.name(), r.admin1(), r.country(), r.countryCode(), r.latitude(), r.longitude(),
                PlaceDto.labelFor(r.name(), r.admin1(), r.country()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<Result> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(Long id, String name, Double latitude, Double longitude,
                  @JsonProperty("feature_code") String featureCode,
                  @JsonProperty("country_code") String countryCode,
                  String country, String admin1) {
    }
}
