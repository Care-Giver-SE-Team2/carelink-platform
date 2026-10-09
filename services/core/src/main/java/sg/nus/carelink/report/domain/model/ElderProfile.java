package sg.nus.carelink.report.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Who the elder is and what they are signed up for, as the elder and care plan tables say when
 * the report is generated.
 *
 * <p>A reading of the profile and care plan tables, not either module's model, for the same
 * reason as {@link VisitFact}. Gender and mobility are kept as the text the table holds. Every
 * component may be null: the tables allow it, and facts built before the profile was read have
 * none of it ({@link #UNKNOWN}).
 *
 * @param planVersion     the version of the care plan in force; null when none is
 * @param planWeeklyHours the plan's weekly hours, rolled up from its tasks
 */
public record ElderProfile(
		String fullName,
		String gender,
		LocalDate dateOfBirth,
		String mobilityLevel,
		Boolean livesAlone,
		String medicalNotes,
		Long primaryCaregiverId,
		String primaryCaregiverName,
		Integer planVersion,
		BigDecimal planWeeklyHours) {

	/** Nothing known: what facts built without a profile carry. */
	public static final ElderProfile UNKNOWN =
			new ElderProfile(null, null, null, null, null, null, null, null, null, null);

	/** Age in whole years on the given day, when the date of birth is on record. */
	public OptionalInt ageOn(LocalDate day) {
		return dateOfBirth == null || dateOfBirth.isAfter(day)
				? OptionalInt.empty()
				: OptionalInt.of(Period.between(dateOfBirth, day).getYears());
	}

	/**
	 * The decade of age on the given day: "80–89". What a reader who may not identify the
	 * elder is told in place of the age itself.
	 */
	public Optional<String> ageBandOn(LocalDate day) {
		OptionalInt age = ageOn(day);
		if (age.isEmpty()) {
			return Optional.empty();
		}
		int from = age.getAsInt() / 10 * 10;
		return Optional.of(from + "–" + (from + 9));
	}

	public boolean hasPlan() {
		return planVersion != null;
	}

	public boolean hasPrimaryCaregiver() {
		return primaryCaregiverId != null;
	}
}
