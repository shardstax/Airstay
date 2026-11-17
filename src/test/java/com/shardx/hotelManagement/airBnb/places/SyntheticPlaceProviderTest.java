package com.shardx.hotelManagement.airBnb.places;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticPlaceProviderTest {

    private final SyntheticPlaceProvider provider = new SyntheticPlaceProvider(new MockEnvironment());

    @Test
    void refusesToStartWithTheProdProfile() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");

        assertThatThrownBy(() -> new SyntheticPlaceProvider(production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never be used with the prod profile");
    }

    @Test
    void startsWithTheTestAndDevProfiles() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatCode(() -> new SyntheticPlaceProvider(dev)).doesNotThrowAnyException();
    }

    @Test
    void inventsAPlaceForAnyId() {
        PlaceDto place = provider.findById(123456L).orElseThrow();

        assertThat(place.getId()).isEqualTo(123456L);
        assertThat(place.getLabel()).isEqualTo("Place 123456, Test Region, Testland");
    }

    @Test
    void searchReturnsOnePlaceNamedAfterTheTypedText() {
        assertThat(provider.search("  Zorbia ", 8)).singleElement().satisfies(place ->
                assertThat(place.getLabel()).isEqualTo("Zorbia, Test Region, Testland"));
    }

    @Test
    void searchGivesTheSameIdForTheSameTextWhateverTheCase() {
        long lower = provider.search("zorbia", 8).get(0).getId();
        long upper = provider.search("ZORBIA", 8).get(0).getId();

        assertThat(lower).isEqualTo(upper).isGreaterThanOrEqualTo(SyntheticPlaceProvider.SYNTHETIC_ID_BASE);
    }

    @Test
    void differentTextGivesDifferentPlaces() {
        assertThat(provider.search("zorbia", 8).get(0).getId()).isNotEqualTo(provider.search("narnia", 8).get(0).getId());
    }
}
