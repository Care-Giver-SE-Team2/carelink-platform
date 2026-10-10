package sg.nus.carelink.shared.validation;

import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link OnOrAfter} on a {@link LocalDate}. */
public class OnOrAfterValidator implements ConstraintValidator<OnOrAfter, LocalDate> {

	private LocalDate earliest;

	@Override
	public void initialize(OnOrAfter constraint) {
		earliest = LocalDate.parse(constraint.value());
	}

	@Override
	public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
		return value == null || !value.isBefore(earliest);
	}
}
