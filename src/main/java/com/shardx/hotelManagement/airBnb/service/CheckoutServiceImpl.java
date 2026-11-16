package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.exception.PaymentProviderException;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Instant;

@Service
@Slf4j
public class CheckoutServiceImpl implements CheckoutService {

    private static final String CURRENCY = "inr";

    @Override
    public CheckoutSession createCheckoutSession(Booking booking, String customerEmail,
                                                 String successUrl, String cancelUrl, Instant expiresAt) {
        log.info("Creating Stripe checkout session for booking with ID: {}", booking.getId());
        long amountInPaise = booking.getAmount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        try {
            SessionCreateParams sessionParams = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    .setCustomerEmail(customerEmail)
                    .setClientReferenceId(booking.getId().toString())
                    .putMetadata("bookingId", booking.getId().toString())
                    .setExpiresAt(expiresAt.getEpochSecond())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(CURRENCY)
                                                    .setUnitAmount(amountInPaise)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(booking.getHotel().getName() + " : " + booking.getRoom().getType())
                                                                    .setDescription("Booking ID: " + booking.getId())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();
            // Same booking + same expiry = same request, so a retried call cannot create a second session.
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("checkout-booking-" + booking.getId() + "-" + expiresAt.getEpochSecond())
                    .build();

            Session session = Session.create(sessionParams, options);
            log.info("Stripe session {} created for booking with ID: {}", session.getId(), booking.getId());
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new PaymentProviderException("Could not start payment, please try again", e);
        }
    }

    @Override
    public String getOpenSessionUrl(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId);
            return "open".equals(session.getStatus()) ? session.getUrl() : null;
        } catch (StripeException e) {
            throw new PaymentProviderException("Could not check payment status, please try again", e);
        }
    }

    @Override
    public void refund(String sessionId, Long bookingId) {
        try {
            Session session = Session.retrieve(sessionId);
            String paymentIntent = session.getPaymentIntent();
            if (paymentIntent == null) {
                log.warn("No payment to refund for booking {} (session {})", bookingId, sessionId);
                return;
            }
            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntent)
                    .build();
            // One refund per booking, even if this is retried.
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("refund-booking-" + bookingId)
                    .build();
            Refund refund = Refund.create(refundParams, options);
            log.info("Refund {} issued for booking {}", refund.getId(), bookingId);
        } catch (StripeException e) {
            throw new PaymentProviderException("Refund failed for booking " + bookingId, e);
        }
    }
}
