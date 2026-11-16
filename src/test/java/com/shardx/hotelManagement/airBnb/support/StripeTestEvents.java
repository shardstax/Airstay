package com.shardx.hotelManagement.airBnb.support;

import com.stripe.Stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Builds Stripe webhook payloads and signs them the same way Stripe does, so the real endpoint verifies them. */
public final class StripeTestEvents {

    private StripeTestEvents() {
    }

    public static String checkoutSessionEvent(String type, String sessionId, String paymentStatus, String sessionStatus) {
        return """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "livemode": false,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "payment_status": "%s",
                      "status": "%s"
                    }
                  }
                }
                """.formatted(UUID.randomUUID().toString().replace("-", ""), Stripe.API_VERSION,
                Instant.now().getEpochSecond(), type, sessionId, paymentStatus, sessionStatus);
    }

    /** Stripe-Signature header: t=timestamp,v1=hex(HMAC-SHA256(secret, timestamp + "." + payload)). */
    public static String signatureHeader(String payload, String webhookSecret) {
        long timestamp = Instant.now().getEpochSecond();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
