package com.shardx.hotelManagement.airBnb.places;

import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceServiceTest {

    private static final PlaceDto PUNE = place(1259229L, "Pune", "Maharashtra", "India");
    private static final PlaceDto PUNO = place(3931276L, "Puno", "Puno", "Peru");

    private final PlaceProvider provider = mock(PlaceProvider.class);
    private PlaceService service;

    @BeforeEach
    void setUp() {
        service = new PlaceService(provider, new PlaceProperties(), Clock.fixed(Instant.parse("2026-12-01T10:00:00Z"), ZoneOffset.UTC));
    }

    private static PlaceDto place(long id, String name, String region, String country) {
        return new PlaceDto(id, name, region, country, "XX", 1.0, 2.0, PlaceDto.labelFor(name, region, country));
    }

    @Test
    void labelsAPlaceWithItsRegionAndCountry() {
        assertThat(PUNE.getLabel()).isEqualTo("Pune, Maharashtra, India");
    }

    @Test
    void leavesTheRegionOutWhenItOnlyRepeatsTheName() {
        assertThat(PlaceDto.labelFor("Singapore", "Singapore", "Singapore")).isEqualTo("Singapore, Singapore");
        assertThat(PlaceDto.labelFor("Punggol", null, "Singapore")).isEqualTo("Punggol, Singapore");
        assertThat(PlaceDto.labelFor("Punggol", " ", "Singapore")).isEqualTo("Punggol, Singapore");
    }

    @Test
    void asksTheProviderOnceAndThenAnswersFromTheCache() {
        when(provider.search("pun", 8)).thenReturn(List.of(PUNE, PUNO));

        assertThat(service.search("pun")).containsExactly(PUNE, PUNO);
        assertThat(service.search("pun")).containsExactly(PUNE, PUNO);

        verify(provider, times(1)).search("pun", 8);
    }

    @Test
    void treatsDifferentSpacingAndCaseAsTheSameSearch() {
        when(provider.search(anyString(), anyInt())).thenReturn(List.of(PUNE));

        service.search("New   York");
        service.search("  new york ");
        service.search("NEW YORK");

        verify(provider, times(1)).search("New York", 8);
    }

    @Test
    void doesNotRememberAnEmptyAnswer() {
        when(provider.search("zzz", 8)).thenReturn(List.of());

        assertThat(service.search("zzz")).isEmpty();
        assertThat(service.search("zzz")).isEmpty();

        verify(provider, times(2)).search("zzz", 8);
    }

    @Test
    void aProviderFailureReachesTheCaller() {
        when(provider.search(anyString(), anyInt())).thenThrow(new PlaceLookupException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.search("pune")).isInstanceOf(PlaceLookupException.class);
    }

    @Test
    void aPlaceSeenInASearchNeedsNoSecondLookupWhenTheHotelIsSaved() {
        when(provider.search("pune", 8)).thenReturn(List.of(PUNE));
        service.search("pune");

        assertThat(service.require(1259229L)).isEqualTo(PUNE);

        verify(provider, never()).findById(1259229L);
    }

    @Test
    void looksAPlaceUpOnceAndRemembersIt() {
        when(provider.findById(1259229L)).thenReturn(Optional.of(PUNE));

        service.require(1259229L);
        service.require(1259229L);

        verify(provider, times(1)).findById(1259229L);
    }

    @Test
    void refusesAPlaceThatDoesNotExist() {
        when(provider.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(42L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown place");
    }

    @Test
    void doesNotRememberAPlaceThatDoesNotExist() {
        when(provider.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(42L)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.require(42L)).isInstanceOf(BadRequestException.class);

        verify(provider, times(2)).findById(42L);
    }
}
