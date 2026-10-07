package sg.nus.carelink.careplan.application;

import java.time.LocalDate;
import java.util.Optional;

import sg.nus.carelink.careplan.domain.model.CarePlan;

/**
 * Cross-module contract: when another module needs an elder's plan status it imports this
 * interface only, and never reaches into careplan's domain or infrastructure. Mirrors
 * identity.application.UserDirectory, the fourth of the four situations that justify an
 * interface (ARCHITECTURE.md section 3).
 */
public interface CarePlanLookup {

	/** The elder's highest-version plan (draft, published or superseded), if any. */
	Optional<CarePlan> findLatestByElderId(Long elderId);

	/**
	 * The earliest date on or after {@code max(from, plan.startDate())} that the elder's active
	 * published plan has a visit scheduled, derived from its nodes' weekly schedule days. Empty
	 * when the elder has no published plan, the plan has no start date yet, or none of its nodes
	 * carry a schedule.
	 */
	Optional<LocalDate> findNextVisitDate(Long elderId, LocalDate from);
}
