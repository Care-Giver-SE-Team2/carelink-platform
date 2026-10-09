package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.VisitInstructionRepository;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

/** An extra service's visit: booked with no plan, its instructions kept for the caregiver. */
class StandaloneVisitsServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 10, 0);

    private final VisitRepository visits = mock(VisitRepository.class);
    private final VisitInstructionRepository instructions = mock(VisitInstructionRepository.class);
    private final StandaloneVisitsService service = new StandaloneVisitsService(visits, instructions);

    @Test
    void schedulesAPlanlessVisitAndKeepsItsInstructions() {
        when(visits.save(any())).thenAnswer(call -> withId(call.getArgument(0), 77L));

        Long id = service.schedule(new StandaloneVisits.NewVisit(10L, 42L, "Hospital escort", START,
                START.plusHours(3), "  Bring the wheelchair  "));

        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<Visit> saved = ArgumentCaptor.forClass(Visit.class);
        verify(visits).save(saved.capture());
        assertThat(saved.getValue().standalone()).isTrue();
        assertThat(saved.getValue().caregiverId()).isEqualTo(42L);
        assertThat(saved.getValue().scheduledEnd()).isEqualTo(START.plusHours(3));
        assertThat(saved.getValue().status()).isEqualTo(Visit.Status.SCHEDULED);
        verify(instructions).save(77L, "Bring the wheelchair");
    }

    @Test
    void blankInstructionsAreNotStoredAndLongOnesAreCut() {
        when(visits.save(any())).thenAnswer(call -> withId(call.getArgument(0), 77L));

        service.schedule(new StandaloneVisits.NewVisit(10L, null, "Companionship", START, null, "  "));
        verify(instructions, never()).save(anyLong(), anyString());

        service.schedule(new StandaloneVisits.NewVisit(10L, null, "Companionship", START, null, "x".repeat(1200)));
        verify(instructions).save(77L, "x".repeat(StandaloneVisitsService.INSTRUCTIONS_MAX));
    }

    @Test
    void findSaysWhetherAnybodyCheckedIn() {
        Visit started = new Visit(77L, 10L, 42L, null, null, "Hospital escort", START, null, START.plusMinutes(3),
                null, Visit.Status.EXCEPTION, null, null, 2, null, null);
        when(visits.findById(77L)).thenReturn(Optional.of(started));

        assertThat(service.find(77L)).contains(new StandaloneVisits.State(77L, 42L, "EXCEPTION", true));
        assertThat(service.find(78L)).isEmpty();
    }

    private static Visit withId(Visit visit, Long id) {
        return new Visit(id, visit.elderId(), visit.caregiverId(), visit.carePlanNodeId(), visit.absenceId(),
                visit.serviceType(), visit.scheduledStart(), visit.scheduledEnd(), visit.checkedInAt(),
                visit.checkedOutAt(), visit.status(), visit.stateDeadline(), visit.carePlanId(), visit.version(),
                visit.createdAt(), visit.updatedAt());
    }
}
