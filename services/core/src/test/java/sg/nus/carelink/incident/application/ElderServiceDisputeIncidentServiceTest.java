package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.support.FakeManagerDirectory;
import sg.nus.carelink.incident.support.InMemoryIncidentLogRepository;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.incident.support.RecordingAlert;

class ElderServiceDisputeIncidentServiceTest {

    private final InMemoryIncidentRepository incidents =
            new InMemoryIncidentRepository();

    private final InMemoryIncidentLogRepository timeline =
            new InMemoryIncidentLogRepository();

    private final RecordingAlert alert =
            new RecordingAlert();

    private final Clock clock =
            IncidentFixtures.clockAt(
                    IncidentFixtures.RAISED_AT
            );

    private IncidentService service;

    @BeforeEach
    void setUp() {
        EscalationService escalation =
                new EscalationService(
                        incidents,
                        timeline,
                        FakeManagerDirectory.with(
                                IncidentFixtures.ALICE,
                                IncidentFixtures.BEN
                        ),
                        alert,
                        EscalationPolicy.defaults(),
                        clock
                );

        service =
                new IncidentService(
                        incidents,
                        timeline,
                        escalation,
                        clock
                );
    }

    @Test
    void elderServiceDisputeIsSavedLoggedAndRouted() {
        Incident incident =
                service.createElderServiceDispute(
                        1L,
                        15L,
                        101L,
                        "Caregiver left early."
                );

        assertThat(incident.id())
                .isNotNull();

        assertThat(incident.elderId())
                .isEqualTo(1L);

        assertThat(incident.visitId())
                .isEqualTo(15L);

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

        assertThat(incident.responderUserId())
                .isEqualTo(
                        IncidentFixtures
                                .ALICE
                                .userId()
                );

        assertThat(incident.respondBy())
                .isNotNull();

        assertThat(
                timeline.actionsFor(
                        incident.id()
                )
        )
                .containsSubsequence(
                        "REPORTED",
                        "BROADCAST",
                        "ASSIGNED"
                );
    }
}