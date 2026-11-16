package com.shardx.hotelManagement.airBnb.controller;

import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import com.shardx.hotelManagement.airBnb.service.BookingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final BookingService bookingService;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    /**
     * 204 = processed (or safely ignored). 400 = not a genuine Stripe request.
     * Any other error returns 5xx so Stripe retries the delivery.
     */
    @PostMapping("/payment")
    @Operation(summary = "Stripe webhook: confirm or release bookings from checkout events", tags = {"Webhook"})
    public ResponseEntity<Void> capturePayments(@RequestBody String payload,
                                                @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException | RuntimeException e) {
            // Bad signature, or a payload that is not valid Stripe JSON.
            throw new BadRequestException("Invalid Stripe webhook request");
        }
        bookingService.capturePayment(event);
        return ResponseEntity.noContent().build();
    }
}
