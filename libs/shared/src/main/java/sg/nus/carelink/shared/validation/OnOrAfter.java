package sg.nus.carelink.shared.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A date no earlier than {@link #value()}, an ISO date such as "1900-01-01". Null passes; pair it
 * with {@code @NotNull} when the date is required.
 */
@Documented
@Constraint(validatedBy = OnOrAfterValidator.class)
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface OnOrAfter {

	String value();

	String message() default "Enter a date on or after {value}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
