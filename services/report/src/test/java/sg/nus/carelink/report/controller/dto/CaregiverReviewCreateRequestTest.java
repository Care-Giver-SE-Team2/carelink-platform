package sg.nus.carelink.report.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import sg.nus.carelink.report.domain.model.CaregiverReview;

class CaregiverReviewCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CaregiverReviewCreateRequest request(LocalDate start, LocalDate end, String feedback) {
        return new CaregiverReviewCreateRequest(21L, 31L, start, end, (byte) 5, (byte) 4, (byte) 5, feedback,
                CaregiverReview.RenewalDecision.RENEW_CURRENT);
    }

    private Set<String> invalidFields(CaregiverReviewCreateRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void acceptsAPastPeriodAndAOneDayPeriod() {
        LocalDate day = LocalDate.now().minusDays(3);
        assertThat(invalidFields(request(day.minusDays(27), day, "Very patient."))).isEmpty();
        assertThat(invalidFields(request(day, day, null))).isEmpty();
    }

    @Test
    void rejectsAPeriodThatEndsBeforeItStarts() {
        LocalDate day = LocalDate.now().minusDays(3);
        assertThat(invalidFields(request(day, day.minusDays(1), null))).containsExactly("periodInOrder");
    }

    @Test
    void rejectsAPeriodThatHasNotEnded() {
        assertThat(invalidFields(request(LocalDate.now().minusDays(3), LocalDate.now().plusDays(1), null)))
                .containsExactly("periodEnd");
    }

    @Test
    void capsFeedbackAt2000Characters() {
        LocalDate day = LocalDate.now().minusDays(3);
        assertThat(invalidFields(request(day, day, "a".repeat(2001)))).containsExactly("feedbackNotes");
    }
}
