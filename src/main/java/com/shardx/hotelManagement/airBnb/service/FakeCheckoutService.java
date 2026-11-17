package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.Booking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A payment provider that never leaves the JVM, for load tests and demos. It replaces Stripe ONLY when
 * {@code airstay.payments.fake=true} (environment variable {@code PAYMENTS_FAKE}), so a test can run the whole booking
 * flow (hold, guests, start payment, signed webhook, confirm, cancel and refund) without a Stripe account or network.
 *
 * <p>The webhook is still verified exactly like Stripe's (signature and all): the test sends a correctly signed
 * {@code checkout.session.completed} event for the session this class hands out. Nothing is charged and no money moves.
 *
 * <p><b>It must never run in production.</b> The application refuses to start if this is enabled together with the
 * {@code prod} profile, and logs a warning whenever it is enabled.
 */
@Service
@Primary
@Slf4j
@ConditionalOnProperty(name = "airstay.payments.fake", havingValue = "true")
public class FakeCheckoutService implements CheckoutService {

    private static final String URL_PREFIX = "https://fake-checkout.invalid/pay/";

    /** Sessions handed out and still payable. */
    private final Set<String> openSessions = ConcurrentHashMap.newKeySet();

    public FakeCheckoutService(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "airstay.payments.fake=true must never be used with the prod profile: it replaces the real payment provider");
        }
        log.warn("FAKE PAYMENTS ARE ENABLED: bookings are paid through an in-memory fake, no money moves. Never use in production.");
    }

    @Override
    public CheckoutSession createCheckoutSession(Booking booking, String customerEmail,
                                                 String successUrl, String cancelUrl, Instant expiresAt) {
        String sessionId = "cs_fake_" + booking.getId() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        openSessions.add(sessionId);
        return new CheckoutSession(sessionId, URL_PREFIX + sessionId);
    }

    @Override
    public String getOpenSessionUrl(String sessionId) {
        return openSessions.contains(sessionId) ? URL_PREFIX + sessionId : null;
    }

    @Override
    public void refund(String sessionId, Long bookingId) {
        log.info("Fake refund for booking {} (session {})", bookingId, sessionId);
    }
}
