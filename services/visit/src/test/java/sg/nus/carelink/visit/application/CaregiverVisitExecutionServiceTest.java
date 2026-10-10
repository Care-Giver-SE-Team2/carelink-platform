package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import sg.nus.carelink.careplan.application.VisitPlanReader;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.visit.domain.model.CheckInLocation;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitExecutionPolicy;
import sg.nus.carelink.visit.domain.model.VisitTask;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;
import sg.nus.carelink.visit.domain.repository.VisitCheckInRepository;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;
import sg.nus.carelink.visit.domain.repository.VisitStateTransitionRepository;
import sg.nus.carelink.visit.domain.repository.VisitTaskRepository;

/** Checking in to a standalone visit - an extra service's work order - gives it its one service task. */
class CaregiverVisitExecutionServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 10, 0);
    private static final CaregiverWorkDirectory.Profile CAREGIVER =
            new CaregiverWorkDirectory.Profile(2L, 20L, "Farah", null, null, null, "AVAILABLE");

    private final CaregiverCommandExecutor executor = mock(CaregiverCommandExecutor.class);
    private final CaregiverCommandStore receipts = mock(CaregiverCommandStore.class);
    private final VisitCommandRepository visits = mock(VisitCommandRepository.class);
    private final VisitTaskRepository tasks = mock(VisitTaskRepository.class);
    private final VisitPlanReader plans = mock(VisitPlanReader.class);
    private CaregiverVisitExecutionService service;
    private Visit visit;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new CaregiverVisitExecutionService(executor, receipts, visits, tasks,
                mock(VisitStateTransitionRepository.class), mock(VisitCheckInRepository.class), plans,
                new VisitExecutionPolicy(Duration.ofMinutes(30), Duration.ofMinutes(10)), mock(MissedCheckInResumeService.class));
        visit = new Visit(1L, 3L, 2L, null, null, "Hospital escort", START, null, null, null,
                Visit.Status.SCHEDULED, null, null, 0, null, null);
        when(executor.now()).thenReturn(START.plusMinutes(2));
        when(executor.execute(eq("farah"), eq(1L), anyString(), any(), any())).thenAnswer(call ->
                ((BiFunction<CaregiverWorkDirectory.Profile, Visit, ?>) call.getArgument(4)).apply(CAREGIVER, visit));
        when(receipts.find(anyLong(), any())).thenReturn(Optional.empty());
        when(visits.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void checkingInCreatesTheServiceTaskAndStartsTheVisit() {
        when(tasks.findByVisitId(1L)).thenReturn(List.of());

        var result = service.checkIn("farah", 1L, 0, UUID.randomUUID(), location());

        ArgumentCaptor<VisitTask> task = ArgumentCaptor.forClass(VisitTask.class);
        verify(tasks).save(task.capture());
        assertThat(task.getValue().name()).isEqualTo("Hospital escort");
        assertThat(task.getValue().carePlanNodeId()).isNull();
        assertThat(result.savedState()).isEqualTo("IN_PROGRESS");
        verifyNoInteractions(plans);
    }

    @Test
    void aServiceTaskAlreadyThereIsNotAddedTwice() {
        when(tasks.findByVisitId(1L)).thenReturn(List.of(visit.standaloneTask()));

        service.checkIn("farah", 1L, 0, UUID.randomUUID(), location());

        verify(tasks, never()).save(any());
    }

    private static CheckInLocation location() {
        return new CheckInLocation("MANUAL_LOCATION_NOTE", null, null, null, "At the door", null);
    }
}
