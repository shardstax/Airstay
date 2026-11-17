package com.shardx.hotelManagement.airBnb.catalog;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;

/**
 * Every entry must be an https address (a photo link) of at most 2048 characters, and there may be at most {@link #max()}
 * of them. http, other schemes (javascript:, data:, file:) and text that is not an address are refused, so a stored value
 * can never turn into a script or a downgraded request when a page shows it.
 */
@Documented
@Constraint(validatedBy = HttpsUrls.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpsUrls {

    int max() default 10;

    String message() default "must be at most 10 https addresses";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<HttpsUrls, String[]> {

        private static final int MAX_LENGTH = 2048;
        private int max;

        @Override
        public void initialize(HttpsUrls annotation) {
            max = annotation.max();
        }

        @Override
        public boolean isValid(String[] values, ConstraintValidatorContext context) {
            if (values == null) {
                return true;
            }
            if (values.length > max) {
                return false;
            }
            for (String value : values) {
                if (!isHttpsUrl(value)) {
                    return false;
                }
            }
            return true;
        }

        static boolean isHttpsUrl(String value) {
            if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
                return false;
            }
            try {
                URI uri = new URI(value);
                return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
            } catch (java.net.URISyntaxException e) {
                return false;
            }
        }
    }
}
