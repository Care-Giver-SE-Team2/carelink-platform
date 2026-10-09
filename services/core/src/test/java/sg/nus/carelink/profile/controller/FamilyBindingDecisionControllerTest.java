package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.FamilyBindingService;
import sg.nus.carelink.profile.controller.dto.FamilyBindingDecisionRequest;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.shared.security.Role;

class FamilyBindingDecisionControllerTest {
    private IdentityService identity;
    private FamilyBindingService service;
    private FamilyBindingDecisionController controller;
    private final Principal principal = () -> "family_test";

    @BeforeEach
    void setUp() {
        identity = mock(IdentityService.class);
        service = mock(FamilyBindingService.class);
        controller = new FamilyBindingDecisionController(identity, service);
        when(identity.require("family_test")).thenReturn(
                new AppUser(20L, "family_test", "Test Family", Set.of(Role.FAMILY), true));
    }

    private ElderFamilyBinding binding(ElderFamilyBinding.Status status) {
        return new ElderFamilyBinding(42L, 1L, 3L, ElderFamilyBinding.Relationship.SON,
                true, ElderFamilyBinding.AccessScope.FULL, status,
                status == ElderFamilyBinding.Status.ACTIVE ? LocalDateTime.of(2026, 10, 9, 10, 0) : null,
                null, null, null);
    }

    private void elderName() {
        when(service.requireElder(1L)).thenReturn(new Elder(1L, 7L, "Test Elder",
                null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void listsIncomingBindingsAndMapsElderName() {
        when(service.listForFamilyUser(20L)).thenReturn(List.of(binding(ElderFamilyBinding.Status.PENDING_CONFIRMATION)));
        elderName();
        var result = controller.list(principal);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(42L);
        assertThat(result.get(0).elderName()).isEqualTo("Test Elder");
        assertThat(result.get(0).status()).isEqualTo(ElderFamilyBinding.Status.PENDING_CONFIRMATION);
        assertThat(result.get(0).primaryContact()).isTrue();
        verify(service).listForFamilyUser(20L);
    }

    @Test
    void listsNoRequests() {
        when(service.listForFamilyUser(20L)).thenReturn(List.of());
        assertThat(controller.list(principal)).isEmpty();
        verify(service, never()).requireElder(anyLong());
    }

    @Test
    void confirmsUsingAuthenticatedUserId() {
        var active = binding(ElderFamilyBinding.Status.ACTIVE);
        when(service.decideForFamilyUser(20L, 42L, true)).thenReturn(active);
        elderName();
        var response = controller.decide(42L, new FamilyBindingDecisionRequest(true), principal);
        assertThat(response.status()).isEqualTo(ElderFamilyBinding.Status.ACTIVE);
        assertThat(response.confirmedAt()).isNotNull();
        verify(service).decideForFamilyUser(20L, 42L, true);
    }

    @Test
    void rejectsUsingAuthenticatedUserId() {
        var rejected = binding(ElderFamilyBinding.Status.REJECTED);
        when(service.decideForFamilyUser(20L, 42L, false)).thenReturn(rejected);
        elderName();
        var response = controller.decide(42L, new FamilyBindingDecisionRequest(false), principal);
        assertThat(response.status()).isEqualTo(ElderFamilyBinding.Status.REJECTED);
        assertThat(response.confirmedAt()).isNull();
        verify(service).decideForFamilyUser(20L, 42L, false);
    }
}
