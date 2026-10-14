package sg.nus.carelink.careplan.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What publishing a care plan needs to know from profile (UC-MG01, UC-MG02): whether the elder's
 * primary caregiver could still take every visit of the new version, or one would fall at the
 * same time as a visit in the plan of another elder they are primary caregiver of. careplan owns
 * the question and profile answers it, since profile already depends on careplan and careplan
 * calling profile would make the two a cycle.
 */
public interface PrimaryCaregiverClashCheck {

	/**
	 * Why the elder's primary caregiver could not take a plan with these tasks from
	 * {@code startDate}, ready to show the manager; empty when the elder has no primary
	 * caregiver who can take visits, or nothing clashes.
	 */
	Optional<String> clashFor(Long elderId, LocalDate startDate, List<CarePlanSchedules.Task> tasks);
}
