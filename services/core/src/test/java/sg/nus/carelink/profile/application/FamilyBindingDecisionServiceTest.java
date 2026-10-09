package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.identity.domain.repository.AppUserRepository;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

class FamilyBindingDecisionServiceTest {
    private FamilyMemberRepository families;
    private ElderFamilyBindingRepository bindings;
    private FamilyBindingService service;

    @BeforeEach
    void setUp() {
        families = mock(FamilyMemberRepository.class);
        bindings = mock(ElderFamilyBindingRepository.class);
        service = new FamilyBindingService(mock(ElderRepository.class), families,
                bindings, mock(AppUserRepository.class));
    }

    private FamilyMember family() {
        return new FamilyMember(3L, 20L, "Test Family", null, null, null, null);
    }

    private ElderFamilyBinding binding(ElderFamilyBinding.Status status) {
        return new ElderFamilyBinding(42L, 1L, 3L, ElderFamilyBinding.Relationship.SON,
                false, ElderFamilyBinding.AccessScope.FULL, status, null, null, null, null);
    }

    @Test
    void listsOnlyBindingsForAuthenticatedFamily() {
        var expected = List.of(binding(ElderFamilyBinding.Status.PENDING_CONFIRMATION));
        when(families.findByUserId(20L)).thenReturn(Optional.of(family()));
        when(bindings.findByFamilyMemberId(3L)).thenReturn(expected);
        assertThat(service.listForFamilyUser(20L)).containsExactlyElementsOf(expected);
        verify(bindings).findByFamilyMemberId(3L);
        verify(bindings, never()).findByElderId(any());
    }

    @Test
    void missingFamilyProfileCannotListBindings() {
        when(families.findByUserId(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listForFamilyUser(20L))
                .isInstanceOf(ResourceNotFound.class);
        verifyNoInteractions(bindings);
    }

    @Test
    void confirmsPendingBindingAndPersistsTimestamp() {
        prepare(ElderFamilyBinding.Status.PENDING_CONFIRMATION);
        when(bindings.save(any(ElderFamilyBinding.class))).thenAnswer(i -> i.getArgument(0));
        var result = service.decideForFamilyUser(20L, 42L, true);
        assertThat(result.status()).isEqualTo(ElderFamilyBinding.Status.ACTIVE);
        assertThat(result.confirmedAt()).isNotNull();
        assertThat(result.familyMemberId()).isEqualTo(3L);
        verify(bindings).save(result);
    }

    @Test
    void rejectsPendingBindingWithoutConfirmationTimestamp() {
        prepare(ElderFamilyBinding.Status.PENDING_CONFIRMATION);
        when(bindings.save(any(ElderFamilyBinding.class))).thenAnswer(i -> i.getArgument(0));
        var result = service.decideForFamilyUser(20L, 42L, false);
        assertThat(result.status()).isEqualTo(ElderFamilyBinding.Status.REJECTED);
        assertThat(result.confirmedAt()).isNull();
        verify(bindings).save(result);
    }

    @Test
    void cannotDecideSomeoneElsesBinding() {
        when(families.findByUserId(20L)).thenReturn(Optional.of(
                new FamilyMember(99L, 20L, "Other", null, null, null, null)));
        when(bindings.findById(42L)).thenReturn(Optional.of(binding(ElderFamilyBinding.Status.PENDING_CONFIRMATION)));
        assertThatThrownBy(() -> service.decideForFamilyUser(20L, 42L, true))
                .isInstanceOf(ResourceNotFound.class);
        verify(bindings, never()).save(any());
    }

    @Test
    void cannotDecideUnknownBinding() {
        when(families.findByUserId(20L)).thenReturn(Optional.of(family()));
        when(bindings.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decideForFamilyUser(20L, 42L, true))
                .isInstanceOf(ResourceNotFound.class);
        verify(bindings, never()).save(any());
    }

    @Test
    void cannotDecideWithoutFamilyProfile() {
        when(families.findByUserId(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decideForFamilyUser(20L, 42L, true))
                .isInstanceOf(ResourceNotFound.class);
        verifyNoInteractions(bindings);
    }

    @Test
    void cannotConfirmAlreadyActiveBinding() {
        prepare(ElderFamilyBinding.Status.ACTIVE);
        assertThatThrownBy(() -> service.decideForFamilyUser(20L, 42L, true))
                .isInstanceOf(BusinessRuleViolation.class);
        verify(bindings, never()).save(any());
    }

    @Test
    void cannotRejectAlreadyRejectedBinding() {
        prepare(ElderFamilyBinding.Status.REJECTED);
        assertThatThrownBy(() -> service.decideForFamilyUser(20L, 42L, false))
                .isInstanceOf(BusinessRuleViolation.class);
        verify(bindings, never()).save(any());
    }

    @Test
    void cannotConfirmRevokedBinding() {
        prepare(ElderFamilyBinding.Status.REVOKED);
        assertThatThrownBy(() -> service.decideForFamilyUser(20L, 42L, true))
                .isInstanceOf(BusinessRuleViolation.class);
        verify(bindings, never()).save(any());
    }

    private void prepare(ElderFamilyBinding.Status status) {
        when(families.findByUserId(20L)).thenReturn(Optional.of(family()));
        when(bindings.findById(42L)).thenReturn(Optional.of(binding(status)));
    }
}
