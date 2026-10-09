package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.domain.model.*;
import sg.nus.carelink.profile.domain.repository.*;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/** Uses the real access resolver so permission regressions cannot hide behind mocked guards. */
class FamilyElderProfileServiceTest {
    private final UserDirectory users = mock(UserDirectory.class);
    private final InMemoryFamilyMemberRepository families = new InMemoryFamilyMemberRepository();
    private final ElderFamilyBindingRepository bindings = mock(ElderFamilyBindingRepository.class);
    private final ElderRepository elders = mock(ElderRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T04:00:00Z"), ZoneOffset.UTC);
    private final FamilyAccessQueryService access = new FamilyAccessQueryService(users, families, bindings, clock);
    private final FamilyElderProfileService service = new FamilyElderProfileService(access, access, elders, bindings);
    private final Elder elder = new Elder(1L, 71L, "Tan Mei", Elder.Gender.FEMALE,
            LocalDate.of(1948, 2, 3), "81234567", "Old address", "123456", "AMK", "Hokkien", true,
            Elder.MobilityLevel.INDEPENDENT, Elder.ContinuityPreference.REQUIRED, "Internal medical notes",
            LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));

    @BeforeEach
    void setup() {
        when(users.findByUsername("family")).thenReturn(Optional.of(
                new AppUser(7L, "family", "Family", Set.of(Role.FAMILY), true)));
        families.save(new FamilyMember(42L, 7L, "Family", null, null, null, null));
        when(elders.findById(1L)).thenReturn(Optional.of(elder));
        when(elders.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private ElderFamilyBinding bind(ElderFamilyBinding.AccessScope scope, ElderFamilyBinding.Status status,
            LocalDateTime expiry) {
        var binding = new ElderFamilyBinding(3L, 1L, 42L, ElderFamilyBinding.Relationship.DAUGHTER,
                true, scope, status, null, expiry, null, null);
        when(bindings.findByElderIdAndFamilyMemberId(1L, 42L)).thenReturn(Optional.of(binding));
        when(bindings.findByFamilyMemberId(42L)).thenReturn(List.of(binding));
        return binding;
    }

    private ElderBasicDetails details() {
        return new ElderBasicDetails(" New name ", Elder.Gender.OTHER, LocalDate.of(1949, 1, 1),
                " 87654321 ", " New address ", "654321", " Mandarin ", false, Elder.MobilityLevel.ASSISTIVE_CANE);
    }

    @Test
    void fullBindingUpdatesBasicsAndPreservesAccountAndManagerFields() {
        bind(ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.ACTIVE, null);
        var result = service.update("family", 1L, details());
        var captor = org.mockito.ArgumentCaptor.forClass(Elder.class);
        verify(elders).save(captor.capture());
        Elder saved = captor.getValue();
        assertThat(saved.fullName()).isEqualTo("New name");
        assertThat(saved.dateOfBirth()).isEqualTo(details().dateOfBirth());
        assertThat(saved.phone()).isEqualTo("87654321");
        assertThat(saved.address()).isEqualTo("New address");
        assertThat(saved.postalCode()).isEqualTo("654321");
        assertThat(saved.preferredDialects()).isEqualTo("Mandarin");
        assertThat(saved.gender()).isEqualTo(Elder.Gender.OTHER);
        assertThat(saved.livesAlone()).isFalse();
        assertThat(saved.mobilityLevel()).isEqualTo(Elder.MobilityLevel.ASSISTIVE_CANE);
        assertThat(saved.id()).isEqualTo(elder.id());
        assertThat(saved.userId()).isEqualTo(elder.userId());
        assertThat(saved.sector()).isEqualTo(elder.sector());
        assertThat(saved.continuityPreference()).isEqualTo(elder.continuityPreference());
        assertThat(saved.medicalNotes()).isEqualTo(elder.medicalNotes());
        assertThat(saved.createdAt()).isEqualTo(elder.createdAt());
        assertThat(saved.updatedAt()).isEqualTo(elder.updatedAt());
        assertThat(result.accessScope()).isEqualTo(ElderFamilyBinding.AccessScope.FULL);
    }

    @Test
    void readOnlyBindingReadsButCannotSave() {
        bind(ElderFamilyBinding.AccessScope.READ_ONLY, ElderFamilyBinding.Status.ACTIVE, null);
        assertThat(service.get("family", 1L).fullName()).isEqualTo("Tan Mei");
        assertThat(service.get("family", 1L).accessScope()).isEqualTo(ElderFamilyBinding.AccessScope.READ_ONLY);
        assertThatThrownBy(() -> service.update("family", 1L, details())).isInstanceOf(AccessDeniedException.class);
        verify(elders, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ElderFamilyBinding.Status.class, names = {"PENDING_CONFIRMATION", "REJECTED", "REVOKED"})
    void inactiveBindingsCannotReadOrWrite(ElderFamilyBinding.Status status) {
        bind(ElderFamilyBinding.AccessScope.FULL, status, null);
        assertThatThrownBy(() -> service.get("family", 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update("family", 1L, details())).isInstanceOf(AccessDeniedException.class);
        assertThat(service.list("family")).isEmpty();
        verify(elders).findByIds(Set.of());
        verify(elders, never()).findById(any());
        verify(elders, never()).save(any());
    }

    @Test
    void expiryAtCurrentSingaporeTimeDeniesBothOperations() {
        bind(ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.ACTIVE,
                LocalDateTime.of(2026, 10, 9, 12, 0));
        assertThatThrownBy(() -> service.get("family", 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update("family", 1L, details())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(elders);
    }

    @Test
    void anotherElderCannotBeAccessedOrUpdated() {
        bind(ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.ACTIVE, null);
        assertThatThrownBy(() -> service.get("family", 2L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update("family", 2L, details())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(elders);
    }

    @Test
    void revokedBindingCannotSaveAfterOpeningAnEditableProfile() {
        bind(ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.ACTIVE, null);
        service.get("family", 1L);
        bind(ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.REVOKED, null);
        assertThatThrownBy(() -> service.update("family", 1L, details())).isInstanceOf(AccessDeniedException.class);
        verify(elders, never()).save(any());
    }

    @Test
    void listingQueriesOnlyReadableIds() {
        bind(ElderFamilyBinding.AccessScope.READ_ONLY, ElderFamilyBinding.Status.ACTIVE, null);
        when(elders.findByIds(Set.of(1L))).thenReturn(List.of(elder));
        assertThat(service.list("family")).extracting(FamilyElderProfile::id).containsExactly(1L);
        verify(elders).findByIds(Set.of(1L));
        verify(elders, never()).findAll();
    }

    @Test
    void noBindingsReturnsEmptyList() {
        assertThat(service.list("family")).isEmpty();
        verify(elders).findByIds(Set.of());
    }

    @Test
    void disabledFamilyCannotAccessProfiles() {
        when(users.findByUsername("family")).thenReturn(Optional.of(
                new AppUser(7L, "family", "Family", Set.of(Role.FAMILY), false)));
        assertThatThrownBy(() -> service.list("family")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get("family", 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update("family", 1L, details())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(elders);
    }

    @Test
    void missingElderIsNotCreatedByUpdate() {
        bind(ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.ACTIVE, null);
        when(elders.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("family", 1L, details())).isInstanceOf(ResourceNotFound.class);
        verify(elders, never()).save(any());
    }

    @Test
    void optionalDetailsCanBeCleared() {
        bind(ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.ACTIVE, null);
        var result = service.update("family", 1L, new ElderBasicDetails("Tan Mei", null, null,
                " ", null, null, "", null, null));
        assertThat(result.phone()).isNull();
        assertThat(result.preferredDialects()).isNull();
        assertThat(result.dateOfBirth()).isNull();
    }
}
