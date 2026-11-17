package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.Booking;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeCheckoutServiceTest {

    private final FakeCheckoutService fake = new FakeCheckoutService(new MockEnvironment());

    @Test
    void handsOutAUniqueSessionPerRequestThatNamesTheBooking() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            CheckoutService.CheckoutSession session = fake.createCheckoutSession(booking(42L), "a@b.test", "s", "c", Instant.now());
            assertThat(session.id()).startsWith("cs_fake_42_");
            assertThat(session.url()).endsWith(session.id());
            ids.add(session.id());
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void aSessionItHandedOutIsOpenAndAnUnknownOneIsNot() {
        CheckoutService.CheckoutSession session = fake.createCheckoutSession(booking(7L), "a@b.test", "s", "c", Instant.now());

        assertThat(fake.getOpenSessionUrl(session.id())).isEqualTo(session.url());
        assertThat(fake.getOpenSessionUrl("cs_fake_999_unknown")).isNull();
    }

    @Test
    void aRefundMovesNoMoneyAndNeverFails() {
        assertThatCode(() -> fake.refund("cs_fake_7_abc", 7L)).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWithTheProdProfile() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");

        assertThatThrownBy(() -> new FakeCheckoutService(production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never be used with the prod profile");
    }

    @Test
    void startsWithTheDevProfile() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatCode(() -> new FakeCheckoutService(dev)).doesNotThrowAnyException();
    }

    // ---- wiring: when does the fake exist, and does it win? --------------------------------------------------

    @org.springframework.context.annotation.Configuration
    static class RealProvider {
        @org.springframework.context.annotation.Bean
        CheckoutService realProvider() {
            return org.mockito.Mockito.mock(CheckoutService.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RealProvider.class, FakeCheckoutService.class);

    @Test
    void doesNotExistUnlessItIsEnabled() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(FakeCheckoutService.class);
            assertThat(context.getBean(CheckoutService.class)).isNotInstanceOf(FakeCheckoutService.class);
        });
    }

    @Test
    void winsOverTheRealProviderWhenEnabled() {
        runner.withPropertyValues("airstay.payments.fake=true").run(context ->
                assertThat(context.getBean(CheckoutService.class)).isInstanceOf(FakeCheckoutService.class));
    }

    @Test
    void enabledTogetherWithTheProdProfileTheApplicationDoesNotStart() {
        runner.withPropertyValues("airstay.payments.fake=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasFailed());
    }

    private static Booking booking(long id) {
        Booking booking = new Booking();
        booking.setId(id);
        return booking;
    }
}
