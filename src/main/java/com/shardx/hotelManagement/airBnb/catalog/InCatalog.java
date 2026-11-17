package com.shardx.hotelManagement.airBnb.catalog;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The value (or every value in the array) must be one of the choices in the {@link Catalog}. Null and empty pass. */
@Documented
@Constraint(validatedBy = {InCatalogValidator.ForString.class, InCatalogValidator.ForStrings.class})
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InCatalog {

    Catalog.Kind value();

    String message() default "contains a value that is not one of the listed choices";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
