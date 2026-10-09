package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.nus.carelink.profile.domain.model.*;
import sg.nus.carelink.profile.domain.repository.*;
import sg.nus.carelink.shared.error.ResourceNotFound;

class FamilyServiceApplicationServiceTest {
    private final FamilyIdentityQuery identity = mock(FamilyIdentityQuery.class);
    private final FamilyAccessQuery access = mock(FamilyAccessQuery.class);
    private final ElderRepository elders = mock(ElderRepository.class);
    private final ServiceApplicationRepository applications = mock(ServiceApplicationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T06:00:00Z"), ZoneId.of("Asia/Singapore"));
    private final FamilyServiceApplicationService service = new FamilyServiceApplicationService(identity, access, elders, applications, clock);
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
}
