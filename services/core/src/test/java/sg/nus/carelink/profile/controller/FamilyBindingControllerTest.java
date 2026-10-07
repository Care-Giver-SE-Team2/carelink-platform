package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.FamilyBindingService;
import sg.nus.carelink.profile.controller.dto.FamilyBindingCreateRequest;
import sg.nus.carelink.profile.controller.dto.FamilyBindingResponse;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.shared.security.Role;

class FamilyBindingControllerTest {

    private IdentityService identityService;
    private FamilyBindingService service;
    private FamilyBindingController controller;

    private Principal principal;
    private AppUser elderUser;

    @BeforeEach
    void setUp() {
        identityService =
                mock(IdentityService.class);

        service =
                mock(FamilyBindingService.class);

        controller =
                new FamilyBindingController(
                        identityService,
                        service
                );

        principal =
                () -> "elder_test";

        /*
         * Current AppUser constructor:
         *
         * id,
         * username,
         * displayName,
         * roles,
         * enabled
         */
        elderUser =
                new AppUser(
                        7L,
                        "elder_test",
                        "Test Elder",
                        Set.of(Role.ELDER),
                        true
                );

        when(
                identityService.require(
                        "elder_test"
                )
        ).thenReturn(elderUser);
    }

    @Test
    void listsCurrentElderBindings() {
        ElderFamilyBinding binding =
                binding(
                        10L,
                        ElderFamilyBinding.Status.ACTIVE
                );

        when(
                service.listForElderUser(7L)
        ).thenReturn(
                List.of(binding)
        );

        when(
                service.requireFamilyMember(3L)
        ).thenReturn(
                familyMember()
        );

        List<FamilyBindingResponse> result =
                controller.list(principal);

        assertThat(result)
                .hasSize(1);

        FamilyBindingResponse response =
                result.get(0);

        assertThat(response.id())
                .isEqualTo(10L);

        assertThat(response.familyMemberId())
                .isEqualTo(3L);

        assertThat(response.familyMemberName())
                .isEqualTo("Family Test");

        assertThat(response.relationship())
                .isEqualTo(
                        ElderFamilyBinding.Relationship.SON
                );

        assertThat(response.primaryContact())
                .isTrue();

        assertThat(response.accessScope())
                .isEqualTo(
                        ElderFamilyBinding.AccessScope.FULL
                );

        assertThat(response.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.ACTIVE
                );

        verify(identityService)
                .require("elder_test");

        verify(service)
                .listForElderUser(7L);

        verify(service)
                .requireFamilyMember(3L);
    }

    @Test
    void returnsEmptyListWhenElderHasNoBindings() {
        when(
                service.listForElderUser(7L)
        ).thenReturn(
                List.of()
        );

        List<FamilyBindingResponse> result =
                controller.list(principal);

        assertThat(result)
                .isEmpty();

        verify(service)
                .listForElderUser(7L);
    }

    @Test
    void createsBindingForCurrentElder() {
        FamilyBindingCreateRequest request =
                new FamilyBindingCreateRequest(
                        "family_test",
                        ElderFamilyBinding.Relationship.SON,
                        true,
                        ElderFamilyBinding.AccessScope.FULL
                );

        ElderFamilyBinding created =
                binding(
                        10L,
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        when(
                service.create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.SON,
                        true,
                        ElderFamilyBinding.AccessScope.FULL
                )
        ).thenReturn(created);

        when(
                service.requireFamilyMember(3L)
        ).thenReturn(
                familyMember()
        );

        FamilyBindingResponse response =
                controller.create(
                        request,
                        principal
                );

        assertThat(response.id())
                .isEqualTo(10L);

        assertThat(response.familyMemberId())
                .isEqualTo(3L);

        assertThat(response.familyMemberName())
                .isEqualTo("Family Test");

        assertThat(response.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        assertThat(response.primaryContact())
                .isTrue();

        verify(identityService)
                .require("elder_test");

        verify(service)
                .create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.SON,
                        true,
                        ElderFamilyBinding.AccessScope.FULL
                );

        verify(service)
                .requireFamilyMember(3L);
    }

    @Test
    void revokesBindingForCurrentElder() {
        ElderFamilyBinding revoked =
                binding(
                        10L,
                        ElderFamilyBinding.Status.REVOKED
                );

        when(
                service.revoke(
                        7L,
                        10L
                )
        ).thenReturn(revoked);

        when(
                service.requireFamilyMember(3L)
        ).thenReturn(
                familyMember()
        );

        FamilyBindingResponse response =
                controller.revoke(
                        10L,
                        principal
                );

        assertThat(response.id())
                .isEqualTo(10L);

        assertThat(response.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.REVOKED
                );

        assertThat(response.familyMemberName())
                .isEqualTo("Family Test");

        verify(identityService)
                .require("elder_test");

        verify(service)
                .revoke(
                        7L,
                        10L
                );

        verify(service)
                .requireFamilyMember(3L);
    }

    private ElderFamilyBinding binding(
            Long id,
            ElderFamilyBinding.Status status) {

        return new ElderFamilyBinding(
                id,
                1L,
                3L,
                ElderFamilyBinding.Relationship.SON,
                true,
                ElderFamilyBinding.AccessScope.FULL,
                status,
                null,
                null,
                null,
                null
        );
    }

    /*
     * Current FamilyMember constructor:
     *
     * id,
     * userId,
     * fullName,
     * phone,
     * residentialAddress,
     * createdAt,
     * updatedAt
     */
    private FamilyMember familyMember() {
        return new FamilyMember(
                3L,
                20L,
                "Family Test",
                null,
                null,
                null,
                null
        );
    }
}