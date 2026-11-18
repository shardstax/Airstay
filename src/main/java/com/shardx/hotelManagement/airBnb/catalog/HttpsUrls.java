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
 * Accepts at most {@link #max()} HTTPS links or exact same-origin catalog photo paths.
 */
@Documented
@Constraint(validatedBy = HttpsUrls.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpsUrls {

    int max() default 10;

    String message() default "must contain at most 10 HTTPS links or uploaded photo paths";

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
                if (!isHttpsUrl(value) && !PhotoController.isPhotoPath(value)) {
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
