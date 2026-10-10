package sg.nus.carelink.careplan.domain.model;

import java.util.Arrays;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The one list of care activities CareLink offers. A family ticks these when applying for care,
 * and a manager builds the elder's plan from the same list, so what was asked for and what can be
 * planned never drift apart. The constant name is the code stored in an application's care_needs
 * (BATHING and VITALS predate this list and keep their codes); the label is what both sides see
 * and what a plan task is named; the category is the sub-plan the task is filed under.
 */
public enum CareActivity {

	BATHING("Bathing assistance", Category.PERSONAL_CARE),
	GROOMING("Grooming", Category.PERSONAL_CARE),
	MEAL_SUPPORT("Meal support", Category.PERSONAL_CARE),
	VITALS("Vital-sign check", Category.HEALTH_MONITORING),
	MORNING_MEDICATION_REMINDER("Morning reminder", Category.MEDICATION_SUPPORT),
	EVENING_MEDICATION_REMINDER("Evening reminder", Category.MEDICATION_SUPPORT),
	COMPANIONSHIP_WALK("Companionship walk", Category.SOCIAL_AND_MOBILITY),
	LIGHT_EXERCISE("Light exercise", Category.SOCIAL_AND_MOBILITY),
	ERRAND_ACCOMPANIMENT("Errand accompaniment", Category.SOCIAL_AND_MOBILITY);

	public enum Category {
		PERSONAL_CARE("Personal care"),
		HEALTH_MONITORING("Health monitoring"),
		MEDICATION_SUPPORT("Medication support"),
		SOCIAL_AND_MOBILITY("Social and mobility");

		private final String label;

		Category(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private final String label;
	private final Category category;

	CareActivity(String label, Category category) {
		this.label = label;
		this.category = category;
	}

	/**
	 * A plan task names the activity it delivers by code, or none for a task outside the catalog;
	 * a code the catalog doesn't know is refused rather than stored, so it can always be matched
	 * against what a family applied for.
	 */
	public static void requireKnownOrAbsent(String code) {
		if (code != null && Arrays.stream(values()).noneMatch(activity -> activity.name().equals(code))) {
			throw new BusinessRuleViolation("CARE_PLAN_UNKNOWN_ACTIVITY",
					"[%s] is not a care activity in the catalog".formatted(code));
		}
	}

	public String code() {
		return name();
	}

	public String label() {
		return label;
	}

	public Category category() {
		return category;
	}
}
