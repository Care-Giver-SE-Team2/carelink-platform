package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.model.Visit;

class VisitServiceTest {

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Singapore");

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-24T03:00:00Z"
            );

    private InMemoryVisitRepository visits;
    private InMemoryElderConfirmationRepository confirmations;
    private IncidentService incidents;
    private VisitService service;

    @BeforeEach
    void setUp() {
        visits =
                new InMemoryVisitRepository();

        confirmations =
                new InMemoryElderConfirmationRepository();

        incidents =
                mock(IncidentService.class);

        service =
                new VisitService(
                        visits,
                        confirmations,
                        incidents,
                        Clock.fixed(
                                NOW,
                                ZONE
                        )
                );
    }

    @Test
    void findsWhatWasSaved() {
        Visit saved =
                visits.save(
                        visit(
                                2L,
                                Visit.Status.SCHEDULED
                        )
                );

        assertThat(
                service.findVisit(
                        saved.id()
                )
        ).contains(saved);
    }

    @Test
    void isEmptyForAnUnknownId() {
        assertThat(
                service.findVisit(
                        999L
                )
        ).isEmpty();
    }

    @Test
    void dayRosterDefaultsToTodayInSingaporeAndLeavesOutCancelledVisits() {
        Visit lateYesterday = visits.save(visitAt(1L, Visit.Status.COMPLETED, LocalDateTime.of(2026, 9, 23, 23, 30)));
        Visit afternoon = visits.save(visitAt(2L, Visit.Status.SCHEDULED, LocalDateTime.of(2026, 9, 24, 14, 0)));
        Visit morning = visits.save(visitAt(3L, Visit.Status.IN_PROGRESS, LocalDateTime.of(2026, 9, 24, 8, 0)));
        visits.save(visitAt(4L, Visit.Status.CANCELLED, LocalDateTime.of(2026, 9, 24, 9, 0)));
        visits.save(visitAt(5L, Visit.Status.SCHEDULED, LocalDateTime.of(2026, 9, 25, 0, 0)));

        assertThat(service.findDayRoster(null)).containsExactly(morning, afternoon);
        assertThat(service.findDayRoster(LocalDate.of(2026, 9, 23))).containsExactly(lateYesterday);
    }

    @Test
    void listsOnlyCompletedVisitsAwaitingConfirmationForTheElder() {
        Visit newest =
                visits.save(
                        visitAt(
                                7L,
                                Visit.Status.COMPLETED,
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        24,
                                        11,
                                        0
                                )
                        )
                );

        Visit older =
                visits.save(
                        visitAt(
                                7L,
                                Visit.Status.COMPLETED,
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        24,
                                        9,
                                        0
                                )
                        )
                );

        Visit scheduled =
                visits.save(
                        visit(
                                7L,
                                Visit.Status.SCHEDULED
                        )
                );

        Visit anotherElder =
                visits.save(
                        visit(
                                99L,
                                Visit.Status.COMPLETED
                        )
                );

        confirmations.save(
                ElderConfirmation.submit(
                        older.id(),
                        7L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "done",
                        LocalDateTime.of(
                                2026,
                                9,
                                24,
                                12,
                                0
                        )
                )
        );

        assertThat(
                service.findAwaitingConfirmation(
                        7L
                )
        )
                .extracting(
                        Visit::id
                )
                .containsExactly(
                        newest.id()
                )
                .doesNotContain(
                        older.id(),
                        scheduled.id(),
                        anotherElder.id()
                );
    }

    @Test
    void confirmedVisitStoresElderConfirmation() {
        Visit visit =
                visits.save(
                        visit(
                                7L,
                                Visit.Status.COMPLETED
                        )
                );

        ElderConfirmation saved =
                service.submitElderConfirmation(
                        7L,
                        101L,
                        visit.id(),
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "  Very good service.  "
                );

        assertThat(saved.id())
                .isNotNull();

        assertThat(saved.visitId())
                .isEqualTo(
                        visit.id()
                );

        assertThat(saved.elderId())
                .isEqualTo(7L);

        assertThat(saved.confirmationStatus())
                .isEqualTo(
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED
                );

        assertThat(saved.rating())
                .isEqualTo((byte) 5);

        assertThat(saved.comment())
                .isEqualTo(
                        "Very good service."
                );

        assertThat(saved.confirmedAt())
                .isEqualTo(
                        LocalDateTime.of(
                                2026,
                                9,
                                24,
                                11,
                                0
                        )
                );

        assertThat(
                confirmations.existsByVisitId(
                        visit.id()
                )
        ).isTrue();

        verifyNoInteractions(
                incidents
        );
    }

    @Test
    void disputedVisitStoresConfirmationAndCreatesIncident() {
        Visit visit =
                visits.save(
                        visit(
                                7L,
                                Visit.Status.COMPLETED
                        )
                );

        ElderConfirmation saved =
                service.submitElderConfirmation(
                        7L,
                        101L,
                        visit.id(),
                        ElderConfirmation
                                .ConfirmationStatus
                                .DISPUTED,
                        (byte) 2,
                        "Caregiver left early."
                );

        assertThat(saved.confirmationStatus())
                .isEqualTo(
                        ElderConfirmation
                                .ConfirmationStatus
                                .DISPUTED
                );

        verify(incidents)
                .createElderServiceDispute(
                        7L,
                        visit.id(),
                        101L,
                        "Caregiver left early."
                );
    }

    @Test
    void disputedVisitWithoutCommentUsesFallbackIncidentDescription() {
        Visit visit =
                visits.save(
                        visit(
                                7L,
                                Visit.Status.COMPLETED
                        )
                );

        service.submitElderConfirmation(
                7L,
                101L,
                visit.id(),
                ElderConfirmation
                        .ConfirmationStatus
                        .DISPUTED,
                null,
                "   "
        );

        verify(incidents)
                .createElderServiceDispute(
                        7L,
                        visit.id(),
                        101L,
                        "Elder disputed completion of the visit."
                );
    }

    @Test
    void rejectsUnknownVisit() {
        assertThatThrownBy(() ->
                service.submitElderConfirmation(
                        7L,
                        101L,
                        999L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        ResourceNotFound.class
                );

        verifyNoInteractions(
                incidents
        );
    }

    @Test
    void hidesVisitBelongingToAnotherElder() {
        Visit visit =
                visits.save(
                        visit(
                                99L,
                                Visit.Status.COMPLETED
                        )
                );

        assertThatThrownBy(() ->
                service.submitElderConfirmation(
                        7L,
                        101L,
                        visit.id(),
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        ResourceNotFound.class
                );

        assertThat(
                confirmations.existsByVisitId(
                        visit.id()
                )
        ).isFalse();
    }

    @Test
    void rejectsVisitThatIsNotCompleted() {
        Visit visit =
                visits.save(
                        visit(
                                7L,
                                Visit.Status.IN_PROGRESS
                        )
                );

        assertThatThrownBy(() ->
                service.submitElderConfirmation(
                        7L,
                        101L,
                        visit.id(),
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        null,
                        null
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
                        "VISIT_NOT_AWAITING_ELDER_CONFIRMATION"
                );
    }

    @Test
    void rejectsSecondConfirmationForSameVisit() {
        Visit visit =
                visits.save(
                        visit(
                                7L,
                                Visit.Status.COMPLETED
                        )
                );

        service.submitElderConfirmation(
                7L,
                101L,
                visit.id(),
                ElderConfirmation
                        .ConfirmationStatus
                        .CONFIRMED,
                (byte) 5,
                "done"
        );

        assertThatThrownBy(() ->
                service.submitElderConfirmation(
                        7L,
                        101L,
                        visit.id(),
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "again"
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
                        "VISIT_ALREADY_CONFIRMED"
                );
    }

    private Visit visit(
            Long elderId,
            Visit.Status status) {

        return visitAt(
                elderId,
                status,
                LocalDateTime.of(
                        2026,
                        9,
                        24,
                        10,
                        0
                )
        );
    }

    private Visit visitAt(
            Long elderId,
            Visit.Status status,
            LocalDateTime scheduledStart) {

        return new Visit(
                null,
                elderId,
                3L,
                4L,
                null,
                "Personal care",
                scheduledStart,
                scheduledStart.plusHours(1),
                scheduledStart,
                status == Visit.Status.COMPLETED
                        ? scheduledStart.plusMinutes(55)
                        : null,
                status,
                null,
                13L,
                1,
                scheduledStart.minusDays(1),
                scheduledStart.minusDays(1)
        );
    }
}