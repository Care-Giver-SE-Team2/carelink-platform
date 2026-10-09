package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.identity.domain.repository.AppUserRepository;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

class FamilyBindingServiceTest {

    private ElderRepository elders;
    private FamilyMemberRepository familyMembers;
    private ElderFamilyBindingRepository bindings;
    private AppUserRepository users;

    private FamilyBindingService service;

    @BeforeEach
    void setUp() {
        elders = mock(ElderRepository.class);
        familyMembers = mock(FamilyMemberRepository.class);
        bindings = mock(ElderFamilyBindingRepository.class);
        users = mock(AppUserRepository.class);

        service = new FamilyBindingService(
                elders,
                familyMembers,
                bindings,
                users
        );
    }

    @Test
    void listsBindingsForAuthenticatedElder() {
        Elder elder = elder(1L, 7L);

        ElderFamilyBinding binding = binding(
                10L,
                1L,
                3L,
                ElderFamilyBinding.Status.ACTIVE
        );

        when(elders.findByUserId(7L))
                .thenReturn(Optional.of(elder));

        when(bindings.findByElderId(1L))
                .thenReturn(List.of(binding));

        List<ElderFamilyBinding> result =
                service.listForElderUser(7L);

        assertThat(result)
                .containsExactly(binding);

        verify(elders)
                .findByUserId(7L);

        verify(bindings)
                .findByElderId(1L);
    }

    @Test
    void createsPendingBindingForFamilyAccount() {
        Elder elder = elder(1L, 7L);
        AppUser familyUser = familyUser();
        FamilyMember familyMember =
                familyMember(3L, 20L);

        when(elders.findByUserId(7L))
                .thenReturn(Optional.of(elder));

        when(users.findByUsername("family_test"))
                .thenReturn(Optional.of(familyUser));

        when(familyMembers.findByUserId(20L))
                .thenReturn(Optional.of(familyMember));

        when(
                bindings.findByElderIdAndFamilyMemberId(
                        1L,
                        3L
                )
        ).thenReturn(Optional.empty());

        when(bindings.save(any(ElderFamilyBinding.class)))
                .thenAnswer(invocation -> {
                    ElderFamilyBinding requested =
                            invocation.getArgument(0);

                    return new ElderFamilyBinding(
                            10L,
                            requested.elderId(),
                            requested.familyMemberId(),
                            requested.relationship(),
                            requested.isPrimaryContact(),
                            requested.accessScope(),
                            requested.status(),
                            requested.confirmedAt(),
                            requested.expiresAt(),
                            requested.createdAt(),
                            requested.updatedAt()
                    );
                });

        ElderFamilyBinding created =
                service.create(
                        7L,
                        "  family_test  ",
                        ElderFamilyBinding.Relationship.SON,
                        true,
                        ElderFamilyBinding.AccessScope.FULL
                );

        assertThat(created.id())
                .isEqualTo(10L);

        assertThat(created.elderId())
                .isEqualTo(1L);

        assertThat(created.familyMemberId())
                .isEqualTo(3L);

        assertThat(created.relationship())
                .isEqualTo(
                        ElderFamilyBinding.Relationship.SON
                );

        assertThat(created.isPrimaryContact())
                .isTrue();

        assertThat(created.accessScope())
                .isEqualTo(
                        ElderFamilyBinding.AccessScope.FULL
                );

        assertThat(created.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        verify(users)
                .findByUsername("family_test");

        verify(familyMembers)
                .findByUserId(20L);
    }

    @Test
    void rejectsUnknownFamilyUsername() {
        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        when(users.findByUsername("missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.create(
                        7L,
                        "missing",
                        ElderFamilyBinding.Relationship.OTHER,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void rejectsAccountThatDoesNotHaveFamilyRole() {
        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        AppUser nonFamilyUser =
                new AppUser(
                        20L,
                        "not_family",
                        "Not Family",
                        Set.of(Role.ELDER),
                        true
                );

        when(users.findByUsername("not_family"))
                .thenReturn(
                        Optional.of(nonFamilyUser)
                );

        assertThatThrownBy(() ->
                service.create(
                        7L,
                        "not_family",
                        ElderFamilyBinding.Relationship.OTHER,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void rejectsDisabledFamilyAccount() {
        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        AppUser disabledFamilyUser =
                new AppUser(
                        20L,
                        "family_test",
                        "Family Test",
                        Set.of(Role.FAMILY),
                        false
                );

        when(users.findByUsername("family_test"))
                .thenReturn(
                        Optional.of(disabledFamilyUser)
                );

        assertThatThrownBy(() ->
                service.create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.SON,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void rejectsFamilyAccountWithoutFamilyProfile() {
        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        when(users.findByUsername("family_test"))
                .thenReturn(
                        Optional.of(
                                familyUser()
                        )
                );

        when(familyMembers.findByUserId(20L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.SON,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void rejectsDuplicatePendingBinding() {
        prepareExistingFamilyAccount();

        ElderFamilyBinding pending =
                binding(
                        10L,
                        1L,
                        3L,
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        when(
                bindings.findByElderIdAndFamilyMemberId(
                        1L,
                        3L
                )
        ).thenReturn(
                Optional.of(pending)
        );

        assertThatThrownBy(() ->
                service.create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.SON,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(
                        BusinessRuleViolation.class
                )
                .hasMessageContaining(
                        "already linked or awaiting confirmation"
                );
    }

    @Test
    void rejectsDuplicateActiveBinding() {
        prepareExistingFamilyAccount();

        ElderFamilyBinding active =
                binding(
                        10L,
                        1L,
                        3L,
                        ElderFamilyBinding.Status.ACTIVE
                );

        when(
                bindings.findByElderIdAndFamilyMemberId(
                        1L,
                        3L
                )
        ).thenReturn(
                Optional.of(active)
        );

        assertThatThrownBy(() ->
                service.create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.SON,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(
                        BusinessRuleViolation.class
                );
    }

    @Test
    void reopensRevokedBindingInsteadOfCreatingDuplicateRow() {
        prepareExistingFamilyAccount();

        ElderFamilyBinding revoked =
                binding(
                        10L,
                        1L,
                        3L,
                        ElderFamilyBinding.Status.REVOKED
                );

        when(
                bindings.findByElderIdAndFamilyMemberId(
                        1L,
                        3L
                )
        ).thenReturn(
                Optional.of(revoked)
        );

        when(bindings.save(any(ElderFamilyBinding.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        ElderFamilyBinding reopened =
                service.create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.DAUGHTER,
                        true,
                        ElderFamilyBinding.AccessScope.READ_ONLY
                );

        assertThat(reopened.id())
                .isEqualTo(10L);

        assertThat(reopened.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        assertThat(reopened.relationship())
                .isEqualTo(
                        ElderFamilyBinding.Relationship.DAUGHTER
                );

        assertThat(reopened.isPrimaryContact())
                .isTrue();

        assertThat(reopened.accessScope())
                .isEqualTo(
                        ElderFamilyBinding.AccessScope.READ_ONLY
                );
    }

    @Test
    void reopensRejectedBindingInsteadOfCreatingDuplicateRow() {
        prepareExistingFamilyAccount();

        ElderFamilyBinding rejected =
                binding(
                        10L,
                        1L,
                        3L,
                        ElderFamilyBinding.Status.REJECTED
                );

        when(
                bindings.findByElderIdAndFamilyMemberId(
                        1L,
                        3L
                )
        ).thenReturn(
                Optional.of(rejected)
        );

        when(bindings.save(any(ElderFamilyBinding.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        ElderFamilyBinding reopened =
                service.create(
                        7L,
                        "family_test",
                        ElderFamilyBinding.Relationship.GUARDIAN,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                );

        assertThat(reopened.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        assertThat(reopened.relationship())
                .isEqualTo(
                        ElderFamilyBinding.Relationship.GUARDIAN
                );
    }

    @Test
    void revokesBindingOwnedByCurrentElder() {
        ElderFamilyBinding existing =
                binding(
                        10L,
                        1L,
                        3L,
                        ElderFamilyBinding.Status.ACTIVE
                );

        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        when(bindings.findById(10L))
                .thenReturn(
                        Optional.of(existing)
                );

        when(bindings.save(any(ElderFamilyBinding.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        ElderFamilyBinding revoked =
                service.revoke(
                        7L,
                        10L
                );

        assertThat(revoked.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.REVOKED
                );

        verify(bindings)
                .save(any(ElderFamilyBinding.class));
    }

    @Test
    void rejectsUnknownBindingDuringRevoke() {
        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        when(bindings.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.revoke(
                        7L,
                        999L
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void hidesBindingOwnedByAnotherElder() {
        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        when(bindings.findById(10L))
                .thenReturn(
                        Optional.of(
                                binding(
                                        10L,
                                        999L,
                                        3L,
                                        ElderFamilyBinding.Status.ACTIVE
                                )
                        )
                );

        assertThatThrownBy(() ->
                service.revoke(
                        7L,
                        10L
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void returnsFamilyMemberById() {
        FamilyMember member =
                familyMember(3L, 20L);

        when(familyMembers.findById(3L))
                .thenReturn(
                        Optional.of(member)
                );

        assertThat(
                service.requireFamilyMember(3L)
        ).isEqualTo(member);
    }

    @Test
    void rejectsUnknownFamilyMemberId() {
        when(familyMembers.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.requireFamilyMember(
                        999L
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void failsWhenAuthenticatedAccountHasNoElderProfile() {
        when(elders.findByUserId(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.listForElderUser(
                        999L
                )
        )
                .isInstanceOf(ResourceNotFound.class);
    }

    private void prepareExistingFamilyAccount() {
        when(elders.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                elder(1L, 7L)
                        )
                );

        when(users.findByUsername("family_test"))
                .thenReturn(
                        Optional.of(
                                familyUser()
                        )
                );

        when(familyMembers.findByUserId(20L))
                .thenReturn(
                        Optional.of(
                                familyMember(
                                        3L,
                                        20L
                                )
                        )
                );
    }

    private Elder elder(
            Long elderId,
            Long userId) {

        return new Elder(
                elderId,
                userId,
                "Test Elder",
                Elder.Gender.OTHER,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Elder.MobilityLevel.INDEPENDENT,
                Elder.ContinuityPreference.NONE,
                null,
                null,
                null
        );
    }

    private AppUser familyUser() {
        return new AppUser(
                20L,
                "family_test",
                "Family Test",
                Set.of(Role.FAMILY),
                true
        );
    }

    private FamilyMember familyMember(
            Long id,
            Long userId) {

        return new FamilyMember(
                id,
                userId,
                "Family Test",
                null,
                null,
                null,
                null
        );
    }

    private ElderFamilyBinding binding(
            Long id,
            Long elderId,
            Long familyMemberId,
            ElderFamilyBinding.Status status) {

        return new ElderFamilyBinding(
                id,
                elderId,
                familyMemberId,
                ElderFamilyBinding.Relationship.SON,
                false,
                ElderFamilyBinding.AccessScope.FULL,
                status,
                null,
                null,
                null,
                null
        );
    }
}