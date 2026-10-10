package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The elder's own confirmation of one completed visit.
 *
 * <p>A row exists only after the elder has answered EL01.
 * CONFIRMED means the elder agrees that the service was completed.
 * DISPUTED means the elder reports a problem with the completed visit.
 */
public record ElderConfirmation(
        Long id,
        Long visitId,
        Long elderId,
        ConfirmationStatus confirmationStatus,
        Byte rating,
        String comment,
        LocalDateTime confirmedAt) {

    public enum ConfirmationStatus {
        CONFIRMED,
        DISPUTED
    }

    /**
     * Creates an EL01 confirmation.
     */
    public static ElderConfirmation submit(
            Long visitId,
            Long elderId,
            ConfirmationStatus status,
            Byte rating,
            String comment,
            LocalDateTime confirmedAt) {

        if (visitId == null) {
            throw new IllegalArgumentException(
                    "visitId must not be null"
            );
        }

        if (elderId == null) {
            throw new IllegalArgumentException(
                    "elderId must not be null"
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "confirmationStatus must not be null"
            );
        }

        if (confirmedAt == null) {
            throw new IllegalArgumentException(
                    "confirmedAt must not be null"
            );
        }

        if (rating != null
                && (rating < 1 || rating > 5)) {

            throw new BusinessRuleViolation(
                    "INVALID_VISIT_RATING",
                    "Visit rating must be between 1 and 5."
            );
        }

        String normalisedComment =
                normaliseComment(comment);

        return new ElderConfirmation(
                null,
                visitId,
                elderId,
                status,
                rating,
                normalisedComment,
                confirmedAt
        );
    }

    private static String normaliseComment(
            String comment) {

        if (comment == null) {
            return null;
        }

        String trimmed = comment.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }
}