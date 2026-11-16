package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.Booking;

import java.time.Instant;

/** Payment-provider boundary (Stripe Checkout). */
public interface CheckoutService {

    record CheckoutSession(String id, String url) {
    }

    CheckoutSession createCheckoutSession(Booking booking, String customerEmail,
                                          String successUrl, String cancelUrl, Instant expiresAt);

    /** The session's payment URL if it can still be paid, otherwise null. */
    String getOpenSessionUrl(String sessionId);

    /** Refunds the payment taken by the session. Safe to call more than once for the same booking. */
    void refund(String sessionId, Long bookingId);
}
