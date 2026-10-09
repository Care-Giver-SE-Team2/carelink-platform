package sg.nus.carelink.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.Incident;

class ElderServiceDisputeIncidentTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(
                    2026,
                    9,
                    24,
                    11,
                    0
            );

    @Test
    void createsServiceIncidentForElderDispute() {
        Incident incident =
                Incident.reportedByElderServiceDispute(
                        1L,
                        15L,
                        101L,
                        "Caregiver left early.",
                        NOW
                );

        assertThat(incident.id())
                .isNull();

        assertThat(incident.elderId())
                .isEqualTo(1L);

        assertThat(incident.visitId())
                .isEqualTo(15L);

        assertThat(incident.reportedByUserId())
                .isEqualTo(101L);

        assertThat(incident.source())
                .isEqualTo(
                        Incident.Source
                                .ELDER_SERVICE_DISPUTE
                );

        assertThat(incident.category())
                .isEqualTo(
                        Incident.Category.SERVICE
                );

        assertThat(incident.severity())
                .isEqualTo(
                        Incident.Severity.MEDIUM
                );

        assertThat(incident.status())
                .isEqualTo(
                        Incident.Status.OPEN
                );

        assertThat(incident.description())
                .isEqualTo(
                        "Caregiver left early."
                );

        assertThat(incident.reportedAt())
                .isEqualTo(NOW);
    }

    @Test
    void requiresElderId() {
        assertThatThrownBy(() ->
                Incident.reportedByElderServiceDispute(
                        null,
                        15L,
                        101L,
                        "problem",
                        NOW
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void requiresVisitId() {
        assertThatThrownBy(() ->
                Incident.reportedByElderServiceDispute(
                        1L,
                        null,
                        101L,
                        "problem",
                        NOW
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void requiresReporter() {
        assertThatThrownBy(() ->
                Incident.reportedByElderServiceDispute(
                        1L,
                        15L,
                        null,
                        "problem",
                        NOW
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void requiresReportedTime() {
        assertThatThrownBy(() ->
                Incident.reportedByElderServiceDispute(
                        1L,
                        15L,
                        101L,
                        "problem",
                        null
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                );
    }
}