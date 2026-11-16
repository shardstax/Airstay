package com.shardx.hotelManagement.airBnb.exception;

/** The payment provider (Stripe) failed or could not be reached. */
public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
