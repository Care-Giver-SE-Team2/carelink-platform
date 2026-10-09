package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/intake-reviews/{id}/approve and /decline: the message the family sees with
 * the decision. Optional to approve; the service requires it to decline. 255 is the length of
 * intake_application.review_remarks.
 */
public record IntakeDecisionRequest(@Size(max = 255) String message) {
}
