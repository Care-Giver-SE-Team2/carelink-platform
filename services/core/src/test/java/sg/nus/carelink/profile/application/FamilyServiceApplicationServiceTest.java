package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Task;
import sg.nus.carelink.profile.domain.model.*;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress;
import sg.nus.carelink.profile.domain.repository.*;
import sg.nus.carelink.shared.error.ResourceNotFound;

class FamilyServiceApplicationServiceTest {
    private final FamilyIdentityQuery identity = mock(FamilyIdentityQuery.class);
    private final FamilyAccessQuery access = mock(FamilyAccessQuery.class);
    private final ElderRepository elders = mock(ElderRepository.class);
    private final ServiceApplicationRepository applications = mock(ServiceApplicationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T06:00:00Z"), ZoneId.of("Asia/Singapore"));
    private final CarePlanSchedules schedules = mock(CarePlanSchedules.class);
    private final FamilyServiceApplicationService service = new FamilyServiceApplicationService(identity, access, elders, applications, clock,
            new CareRequestProgress(schedules));
    private final Elder elder = new Elder(1L, 71L, "Tan Mei", null, null, null, "Road", "123456", null,
            null, null, null, Elder.ContinuityPreference.PREFERRED, "Private notes", null, null);

    @BeforeEach
    void setup() {
        when(identity.requireFamilyMemberId("family")).thenReturn(42L);
        when(elders.findById(1L)).thenReturn(Optional.of(elder));
        when(applications.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void submitsForSessionFamilyUsingSavedProfileAndUtcClock() {
        var result = service.submit("family", 1L, List.of("VITALS"), "New notes");
        assertThat(result.applicantFamilyMemberId()).isEqualTo(42L);
        assertThat(result.elderSnapshot().fullName()).isEqualTo("Tan Mei");
        assertThat(result.notes()).isEqualTo("New notes");
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 9, 6, 0));
        verify(access).requireWritableElder("family", 1L);
        verify(elders, never()).save(any());
    }

    @Test
    void deniesWritesBeforeLoadingProtectedDetails() {
        doThrow(new AccessDeniedException("Denied")).when(access).requireWritableElder("family", 1L);
        assertThatThrownBy(() -> service.submit("family", 1L, List.of("VITALS"), null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(elders, applications);
    }

    @Test
    void missingElderDoesNotCreateAnApplication() {
        when(elders.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit("family", 1L, List.of("VITALS"), null)).isInstanceOf(ResourceNotFound.class);
        verifyNoInteractions(applications);
    }

    @Test
    void failedStorageDoesNotReturnSubmissionSuccess() {
        when(applications.save(any())).thenThrow(new IllegalStateException("Storage unavailable"));
        assertThatThrownBy(() -> service.submit("family", 1L, List.of("VITALS"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filtersListByOwnershipAndCurrentReadableElders() {
        when(access.readableElderIds("family")).thenReturn(Set.of(1L));
        var page = new ServiceApplicationPage(List.of(), 2, 20, 0);
        when(applications.findForApplicant(42L, Set.of(1L), 2, 20)).thenReturn(page);
        assertThat(service.list("family", 2, 20)).isEqualTo(page);
    }

    @Test
    void ownDetailRequiresCurrentReadAccess() {
        var item = ServiceApplication.submit(42L, elder, List.of("VITALS"), null, LocalDateTime.now());
        when(applications.findById(5L)).thenReturn(Optional.of(item));
        assertThat(service.get("family", 5L)).isEqualTo(item);
        verify(access).requireReadableElder("family", 1L);
    }

    @Test
    void anotherApplicantAndUnknownIdBothReturnNotFound() {
        var other = ServiceApplication.submit(43L, elder, List.of("VITALS"), null, LocalDateTime.now());
        when(applications.findById(5L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.get("family", 5L)).isInstanceOf(ResourceNotFound.class);
        assertThatThrownBy(() -> service.get("family", 99L)).isInstanceOf(ResourceNotFound.class);
        verifyNoInteractions(access);
    }

    @Test
    void saysHowFarEachReadableApplicationHasBeenPlanned() {
        // Applied 9 Oct 14:00 in Singapore; v2 from 20 Oct plans bathing, nothing plans meals yet.
        var applied = LocalDateTime.of(2026, 10, 9, 6, 0);
        var bathing = new ServiceApplication(5L, 42L, 1L, elderSnapshot(), List.of("BATHING"), null,
                ServiceApplication.Status.SUBMITTED, applied);
        var both = new ServiceApplication(6L, 42L, 1L, elderSnapshot(), List.of("BATHING", "MEAL_SUPPORT"), null,
                ServiceApplication.Status.SUBMITTED, applied);
        when(schedules.forElder(1L)).thenReturn(List.of(new PlanSchedule(70L, 1L, 2, LocalDate.of(2026, 10, 20), null,
                List.of(new Task(700L, "Personal care", "BATHING", "Bathing assistance", List.of())))));

        var progress = service.progress(List.of(bathing, both));

        assertThat(progress.get(5L).outcome()).isEqualTo(ServiceApplicationProgress.Outcome.PLANNED);
        assertThat(progress.get(5L).needs().getFirst().plannedVersion()).isEqualTo(2);
        assertThat(progress.get(6L).outcome()).isEqualTo(ServiceApplicationProgress.Outcome.SUBMITTED);
        verify(schedules, times(1)).forElder(1L);
    }

    private ElderBasicDetails elderSnapshot() {
        return new ElderBasicDetails("Tan Mei", null, null, null, "Road", "123456", null, null, null);
    }
}
