package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.domain.model.*;
import sg.nus.carelink.profile.domain.repository.*;

class CaregiverWorkDirectoryServiceTest {
    final UserDirectory users = mock(UserDirectory.class);
    final CaregiverRepository caregivers = mock(CaregiverRepository.class);
    final ElderRepository elders = mock(ElderRepository.class);
    final CredentialRepository credentials = mock(CredentialRepository.class);
    final CredentialTypeRepository types = mock(CredentialTypeRepository.class);
    final CaregiverWorkDirectoryService service = new CaregiverWorkDirectoryService(users,caregivers,elders,credentials,types,30);
    final LocalDate day = LocalDate.of(2026,9,24);
    Credential credential(long id, int offset, Credential.Status status) {
        return new Credential(id,1L,2L,null,"CERT-" + id,null,null,
                day.plusDays(offset),status,null,null,null);
    }
    @Test void warningsUseInclusiveWindowAndExcludeRejectedPendingAndRevoked() {
        var rows = List.of(
            credential(1,-1,Credential.Status.PUBLISHED),credential(2,0,Credential.Status.PUBLISHED),
            credential(3,30,Credential.Status.PUBLISHED),credential(4,31,Credential.Status.PUBLISHED),
            credential(5,-1,Credential.Status.REJECTED),credential(6,1,Credential.Status.SUBMITTED),
            credential(7,-1,Credential.Status.REVOKED));
        when(credentials.findByCaregiverId(1L)).thenReturn(rows);
        var alerts = service.alerts(1L,day).items();
        assertThat(alerts).extracting(CaregiverWorkDirectory.CredentialAlert::id).containsExactly(1L,2L,3L);
        assertThat(alerts).extracting(CaregiverWorkDirectory.CredentialAlert::warning).containsExactly("EXPIRED","EXPIRING","EXPIRING");
    }
    @Test void warningsSortByExpiryThenIdRegardlessOfSharedRepositoryOrder() {
        var rows = List.of(
            credential(9,10,Credential.Status.PUBLISHED), credential(5,-1,Credential.Status.EXPIRED),
            credential(3,10,Credential.Status.PUBLISHED));
        when(credentials.findByCaregiverId(1L)).thenReturn(rows);
        assertThat(service.alerts(1L,day).items()).extracting(CaregiverWorkDirectory.CredentialAlert::id)
            .containsExactly(5L,3L,9L);
    }
    @Test void elderProjectionNeverReadsMedicalNotes() {
        var elder = mock(Elder.class);
        when(elder.id()).thenReturn(1L);
        when(elder.fullName()).thenReturn("Mei");
        when(elder.preferredDialects()).thenReturn("English, Mandarin, ");
        when(elders.findById(1L)).thenReturn(Optional.of(elder));
        var result = service.elder(1L);
        assertThat(result.languageNeeds()).containsExactly("English","Mandarin");
        assertThat(result.accessNotes()).isNull();
        assertThat(result.emergencyNotes()).isNull();
        verify(elder,never()).medicalNotes();
    }
    @Test void absentAccountIsDeniedBeforeProfileLookup() {
        assertThatThrownBy(() -> service.require("missing")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(caregivers);
    }

    @Test void batchTypeLookupPreservesTheCaregiverOnlyProjectionAndServerContext() {
        var rows = List.of(credential(1,-2,Credential.Status.PUBLISHED), credential(2,3,Credential.Status.EXPIRING));
        when(credentials.findByCaregiverId(1L)).thenReturn(rows);
        when(types.findByIds(Set.of(2L))).thenReturn(List.of(new CredentialType(2L,"First aid",null)));
        var result = service.alerts(1L,day);
        assertThat(result.items()).extracting(CaregiverWorkDirectory.CredentialAlert::name).containsExactly("First aid","First aid");
        assertThat(result.items()).extracting(CaregiverWorkDirectory.CredentialAlert::daysUntilExpiry).containsExactly(-2L,3L);
        assertThat(result.context()).isEqualTo(new CaregiverWorkDirectory.CredentialAlertContext(day,30,false));
        verify(types).findByIds(Set.of(2L));
        verify(types,never()).findById(any());
        verify(credentials,never()).save(any());
    }

    @Test void missingTypeNameRequiresReviewInsteadOfSilentlyClaimingEverythingIsValid() {
        var row = credential(1,-2,Credential.Status.PUBLISHED);
        when(credentials.findByCaregiverId(1L)).thenReturn(List.of(row));
        var result = service.alerts(1L,day);
        assertThat(result.context().reviewRequired()).isTrue();
        assertThat(result.items().getFirst().name()).isEqualTo("Credential");
    }
}
