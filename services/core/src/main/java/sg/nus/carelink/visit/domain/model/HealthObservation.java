package sg.nus.carelink.visit.domain.model;

/** A caregiver's observation, not an automated diagnosis or an incident notification. */
public record HealthObservation(Flag healthFlag, String healthNote) {
    public enum Flag { NO_CONCERN, ATTENTION, MEDICAL_REVIEW }
}
