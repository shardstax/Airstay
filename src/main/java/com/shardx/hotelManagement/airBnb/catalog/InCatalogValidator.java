package com.shardx.hotelManagement.airBnb.catalog;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;

/** Checks {@link InCatalog} on a single string and on an array of strings. */
public final class InCatalogValidator {

    private InCatalogValidator() {
    }

    public static final class ForString implements ConstraintValidator<InCatalog, String> {
        private List<String> allowed;

        @Override
        public void initialize(InCatalog annotation) {
            allowed = annotation.value().choices();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || allowed.contains(value);
        }
    }

    public static final class ForStrings implements ConstraintValidator<InCatalog, String[]> {
        private List<String> allowed;

        @Override
        public void initialize(InCatalog annotation) {
            allowed = annotation.value().choices();
        }

        @Override
        public boolean isValid(String[] values, ConstraintValidatorContext context) {
            if (values == null) {
                return true;
            }
            // The message never repeats what the client sent: validation messages are templates, and a template built from
            // user input can be abused to run expressions.
            return Arrays.stream(values).allMatch(allowed::contains);
        }
    }
}
