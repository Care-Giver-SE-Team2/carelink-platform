package sg.nus.carelink.visit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.visit.domain.model.ElderConfirmation;

class ElderConfirmationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(
                    2026,
                    9,
                    24,
                    11,
                    0
            );

    @Test
    void createsConfirmedResponseAndNormalisesComment() {
        ElderConfirmation confirmation =
                ElderConfirmation.submit(
                        15L,
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "  Very good service.  ",
                        NOW
                );

        assertThat(confirmation.id())
                .isNull();

        assertThat(confirmation.visitId())
                .isEqualTo(15L);

        assertThat(confirmation.elderId())
                .isEqualTo(7L);

        assertThat(confirmation.confirmationStatus())
                .isEqualTo(
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED
                );

        assertThat(confirmation.rating())
                .isEqualTo((byte) 5);

        assertThat(confirmation.comment())
                .isEqualTo(
                        "Very good service."
                );

        assertThat(confirmation.confirmedAt())
                .isEqualTo(NOW);
    }

    @Test
    void blankCommentBecomesNull() {
        ElderConfirmation confirmation =
                ElderConfirmation.submit(
                        15L,
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .DISPUTED,
                        null,
                        "   ",
                        NOW
                );

        assertThat(confirmation.comment())
                .isNull();
    }

    @Test
    void ratingMayBeOmitted() {
        ElderConfirmation confirmation =
                ElderConfirmation.submit(
                        15L,
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        null,
                        null,
                        NOW
                );

        assertThat(confirmation.rating())
                .isNull();
    }

    @Test
    void rejectsRatingBelowOne() {
        assertThatThrownBy(() ->
                ElderConfirmation.submit(
                        15L,
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 0,
                        null,
                        NOW
                )
        )
                .isInstanceOf(
                        BusinessRuleViolation.class
                )
                .extracting(error ->
                        ((BusinessRuleViolation) error)
                                .code()
                )
                .isEqualTo(
                        "INVALID_VISIT_RATING"
                );
    }

    @Test
    void rejectsRatingAboveFive() {
        assertThatThrownBy(() ->
                ElderConfirmation.submit(
                        15L,
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 6,
                        null,
                        NOW
                )
        )
                .isInstanceOf(
                        BusinessRuleViolation.class
                );
    }

    @Test
    void requiresVisitId() {
        assertThatThrownBy(() ->
                ElderConfirmation.submit(
                        null,
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        null,
                        null,
                        NOW
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void requiresElderId() {
        assertThatThrownBy(() ->
                ElderConfirmation.submit(
                        15L,
                        null,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        null,
                        null,
                        NOW
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void requiresStatus() {
        assertThatThrownBy(() ->
                ElderConfirmation.submit(
                        15L,
                        7L,
                        null,
                        null,
                        null,
                        NOW
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void requiresConfirmationTime() {
        assertThatThrownBy(() ->
                ElderConfirmation.submit(
                        15L,
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        null,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }
}