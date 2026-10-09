package sg.nus.carelink.profile.domain.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * One elder, one record. Two people are taken to be the same elder when they have the same name
 * (ignoring case and spacing) and the same postcode: the intake form asks for no ID number or
 * date of birth, so that is the strongest match its details allow.
 *
 * <p>Checked when a family submits an application, so they are told straight away rather than
 * waiting for a review, and again when a manager approves one, in case the elder was added in
 * between. Callers pass only the elders and pending applications at the postcode in question.
 */
public final class DuplicateElderRule {

	/** The family already has an application for this person waiting for review. */
	public static final String ALREADY_APPLIED = "APPLICATION_ALREADY_SUBMITTED";
	/** The person is on record, or another family's application for them is waiting. Deliberately one code for both. */
	public static final String ALREADY_REGISTERED = "ELDER_ALREADY_REGISTERED";

	private DuplicateElderRule() {
	}

	/**
	 * Refuses a new application for someone already on record or already applied for.
	 *
	 * @param applicantFamilyMemberId the family member submitting
	 * @throws BusinessRuleViolation {@link #ALREADY_APPLIED} if this family member already has one
	 *         waiting; otherwise {@link #ALREADY_REGISTERED} if the person is on record or another
	 *         family has applied. The second message does not say which, so it reveals no more
	 *         than that the care team already knows them.
	 */
	public static void requireNewApplication(String elderName, String postalCode, Long applicantFamilyMemberId,
			List<Elder> eldersAtPostcode, List<IntakeApplication> pendingAtPostcode) {
		List<IntakeApplication> pending = pendingAtPostcode.stream()
				.filter(IntakeApplication::isPending)
				.filter(a -> same(elderName, postalCode, a.targetElderName(), a.postalCode()))
				.toList();
		if (pending.stream().anyMatch(a -> Objects.equals(a.applicantFamilyMemberId(), applicantFamilyMemberId))) {
			throw new BusinessRuleViolation(ALREADY_APPLIED,
					"You already have an application for " + elderName.strip() + " waiting for review.");
		}
		if (!pending.isEmpty() || eldersAtPostcode.stream().anyMatch(e -> same(elderName, postalCode, e.fullName(), e.postalCode()))) {
			throw alreadyRegistered(elderName);
		}
	}

	/**
	 * Refuses to create an elder record for someone already on record.
	 *
	 * @throws BusinessRuleViolation {@link #ALREADY_REGISTERED}
	 */
	public static void requireNotOnRecord(String elderName, String postalCode, List<Elder> eldersAtPostcode) {
		if (eldersAtPostcode.stream().anyMatch(e -> same(elderName, postalCode, e.fullName(), e.postalCode()))) {
			throw alreadyRegistered(elderName);
		}
	}

	static boolean same(String nameA, String postcodeA, String nameB, String postcodeB) {
		return name(nameA).equals(name(nameB)) && strip(postcodeA).equals(strip(postcodeB));
	}

	private static BusinessRuleViolation alreadyRegistered(String elderName) {
		return new BusinessRuleViolation(ALREADY_REGISTERED, elderName.strip()
				+ " at this postcode is already registered with the care team or has an application in progress.");
	}

	private static String name(String name) {
		return strip(name).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static String strip(String text) {
		return Objects.requireNonNullElse(text, "").strip();
	}
}
