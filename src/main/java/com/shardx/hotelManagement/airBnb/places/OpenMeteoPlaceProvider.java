package com.shardx.hotelManagement.airBnb.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

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

    private final RestClient client;

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
                            .queryParam("name", query)
                            // Ask for more than needed: non-city results are dropped below.
                            .queryParam("count", Math.min(100, limit * 3))
                            .queryParam("language", "en")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);
            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results().stream()
                    .filter(OpenMeteoPlaceProvider::isUsablePopulatedPlace)
                    .map(OpenMeteoPlaceProvider::toPlace)
                    .limit(limit)
                    .toList();
        } catch (RestClientException e) {
            throw new PlaceLookupException("Place search is temporarily unavailable", e);
        }
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
