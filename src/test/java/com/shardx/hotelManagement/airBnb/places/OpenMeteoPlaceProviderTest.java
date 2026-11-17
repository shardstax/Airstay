package com.shardx.hotelManagement.airBnb.places;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The provider's parsing and failure handling, against recorded Open-Meteo answers (no Internet needed). */
class OpenMeteoPlaceProviderTest {

    private static final String SEARCH_PUN = """
            {"results":[
              {"id":1259229,"name":"Pune","latitude":18.51957,"longitude":73.85535,"feature_code":"PPL","country_code":"IN",
               "admin1":"Maharashtra","country":"India","population":3124458,"timezone":"Asia/Kolkata"},
              {"id":1259239,"name":"Punāsa","latitude":22.23507,"longitude":76.39335,"feature_code":"PPL","country_code":"IN",
               "admin1":"Madhya Pradesh","country":"India"},
              {"id":1264418,"name":"Maharashtra","latitude":19.0,"longitude":76.0,"feature_code":"ADM1","country_code":"IN",
               "country":"India"},
              {"id":1880348,"name":"Punggol","latitude":1.41444,"longitude":103.90694,"feature_code":"PPL","country_code":"SG",
               "country":"Singapore"},
              {"id":999,"name":"No coordinates","feature_code":"PPL","country_code":"IN","country":"India"}
            ],"generationtime_ms":0.2}
            """;

    private MockRestServiceServer server;
    private OpenMeteoPlaceProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenMeteoPlaceProvider(builder, new PlaceProperties());
    }

    @Test
    void turnsPopulatedPlacesIntoLabelledPlaces() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(queryParam("name", "pun"))
                .andExpect(queryParam("language", "en"))
                .andRespond(withSuccess(SEARCH_PUN, MediaType.APPLICATION_JSON));

        List<PlaceDto> places = provider.search("pun", 8);

        assertThat(places).extracting(PlaceDto::getLabel).containsExactly(
                "Pune, Maharashtra, India", "Punāsa, Madhya Pradesh, India", "Punggol, Singapore");
        PlaceDto pune = places.get(0);
        assertThat(pune.getId()).isEqualTo(1259229L);
        assertThat(pune.getLatitude()).isEqualTo(18.51957);
        assertThat(pune.getCountryCode()).isEqualTo("IN");
        server.verify();
    }

    @Test
    void dropsRegionsCountriesAndEntriesMissingRequiredFields() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andRespond(withSuccess(SEARCH_PUN, MediaType.APPLICATION_JSON));

        List<PlaceDto> places = provider.search("pun", 8);

        assertThat(places).extracting(PlaceDto::getName).doesNotContain("Maharashtra", "No coordinates");
    }

    @Test
    void returnsAtMostTheRequestedNumber() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andRespond(withSuccess(SEARCH_PUN, MediaType.APPLICATION_JSON));

        assertThat(provider.search("pun", 2)).hasSize(2);
    }

    @Test
    void asksForMoreThanItNeedsBecauseSomeResultsAreDropped() {
        server.expect(queryParam("count", "24"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provider.search("pun", 8);

        server.verify();
    }

    @Test
    void treatsNoResultsAsAnEmptyList() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andRespond(withSuccess("{\"generationtime_ms\":0.1}", MediaType.APPLICATION_JSON));

        assertThat(provider.search("qqqqq", 8)).isEmpty();
    }

    @Test
    void reportsAnOutageAsAPlaceLookupFailure() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provider.search("pune", 8)).isInstanceOf(PlaceLookupException.class);
    }

    @Test
    void findsOnePlaceById() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/get")))
                .andExpect(queryParam("id", "1259229"))
                .andRespond(withSuccess("""
                        {"id":1259229,"name":"Pune","latitude":18.51957,"longitude":73.85535,"feature_code":"PPL",
                         "country_code":"IN","admin1":"Maharashtra","country":"India"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.findById(1259229L)).get().extracting(PlaceDto::getLabel).isEqualTo("Pune, Maharashtra, India");
    }

    @Test
    void anUnknownIdIsAnAnswerNotAnOutage() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/get")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":true,\"reason\":\"not found\"}"));

        assertThat(provider.findById(1L)).isEmpty();
    }

    @Test
    void anIdThatIsNotAPopulatedPlaceIsNotUsable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/get")))
                .andRespond(withSuccess("""
                        {"id":1264418,"name":"Maharashtra","latitude":19.0,"longitude":76.0,"feature_code":"ADM1",
                         "country_code":"IN","country":"India"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.findById(1264418L)).isEmpty();
    }

    @Test
    void aServerErrorOnLookupIsAnOutage() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geocoding-api.open-meteo.com/v1/get")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> provider.findById(1259229L)).isInstanceOf(PlaceLookupException.class);
    }
}
