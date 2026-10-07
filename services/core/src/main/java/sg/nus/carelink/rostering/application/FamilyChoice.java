package sg.nus.carelink.rostering.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A family's answer to a change (UC-MG04 step 5): keep the suggested replacement or pick another
 * of the options, move the visit, or skip it.
 *
 * @param caregiverId for {@code CHANGE_CAREGIVER}: one of the options offered; null keeps the suggestion
 * @param newStart for {@code RESCHEDULE}: when the visit should happen instead
 */
public record FamilyChoice(Kind kind, Long caregiverId, LocalDateTime newStart) {

	public FamilyChoice {
		Objects.requireNonNull(kind, "kind");
	}

	public static FamilyChoice keepSuggestion() {
		return new FamilyChoice(Kind.CHANGE_CAREGIVER, null, null);
	}

	public static FamilyChoice pick(Long caregiverId) {
		return new FamilyChoice(Kind.CHANGE_CAREGIVER, caregiverId, null);
	}

	public static FamilyChoice moveTo(LocalDateTime newStart) {
		return new FamilyChoice(Kind.RESCHEDULE, null, newStart);
	}

	public static FamilyChoice skip() {
		return new FamilyChoice(Kind.SKIP, null, null);
	}

	/** The three options of step 4: "更换护理员、改期、本次跳过". */
	public enum Kind {
		CHANGE_CAREGIVER, RESCHEDULE, SKIP
	}
}
