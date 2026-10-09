package sg.nus.carelink.profile.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

class ElderFamilyBindingTest {

    @Test
    void createsPendingBindingRequest() {
        ElderFamilyBinding binding =
                ElderFamilyBinding.request(
                        1L,
                        3L,
                        ElderFamilyBinding.Relationship.SON,
                        true,
                        ElderFamilyBinding.AccessScope.FULL
                );

        assertThat(binding.id()).isNull();
        assertThat(binding.elderId()).isEqualTo(1L);
        assertThat(binding.familyMemberId()).isEqualTo(3L);
        assertThat(binding.relationship())
                .isEqualTo(ElderFamilyBinding.Relationship.SON);
        assertThat(binding.isPrimaryContact()).isTrue();
        assertThat(binding.accessScope())
                .isEqualTo(ElderFamilyBinding.AccessScope.FULL);
        assertThat(binding.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );
        assertThat(binding.confirmedAt()).isNull();
    }

    @Test
    void rejectsRequestWithoutElderId() {
        assertThatThrownBy(() ->
                ElderFamilyBinding.request(
                        null,
                        3L,
                        ElderFamilyBinding.Relationship.SON,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("elderId is required");
    }

    @Test
    void rejectsRequestWithoutFamilyMemberId() {
        assertThatThrownBy(() ->
                ElderFamilyBinding.request(
                        1L,
                        null,
                        ElderFamilyBinding.Relationship.SON,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("familyMemberId is required");
    }

    @Test
    void rejectsRequestWithoutRelationship() {
        assertThatThrownBy(() ->
                ElderFamilyBinding.request(
                        1L,
                        3L,
                        null,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("relationship is required");
    }

    @Test
    void rejectsRequestWithoutAccessScope() {
        assertThatThrownBy(() ->
                ElderFamilyBinding.request(
                        1L,
                        3L,
                        ElderFamilyBinding.Relationship.SON,
                        false,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accessScope is required");
    }

    @Test
    void revokesExistingBinding() {
        ElderFamilyBinding binding =
                existing(
                        ElderFamilyBinding.Status.ACTIVE
                );

        ElderFamilyBinding revoked =
                binding.revoke();

        assertThat(revoked.id())
                .isEqualTo(binding.id());

        assertThat(revoked.elderId())
                .isEqualTo(binding.elderId());

        assertThat(revoked.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.REVOKED
                );
    }

    @Test
    void refusesToRevokeAlreadyRevokedBinding() {
        ElderFamilyBinding binding =
                existing(
                        ElderFamilyBinding.Status.REVOKED
                );

        assertThatThrownBy(binding::revoke)
                .isInstanceOf(
                        BusinessRuleViolation.class
                )
                .hasMessageContaining(
                        "already been removed"
                );
    }

    @Test
    void reopensRevokedBindingAsPending() {
        ElderFamilyBinding binding =
                existing(
                        ElderFamilyBinding.Status.REVOKED
                );

        ElderFamilyBinding reopened =
                binding.requestAgain(
                        ElderFamilyBinding.Relationship.DAUGHTER,
                        true,
                        ElderFamilyBinding.AccessScope.READ_ONLY
                );

        assertThat(reopened.id())
                .isEqualTo(binding.id());

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

        assertThat(reopened.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        assertThat(reopened.confirmedAt())
                .isNull();
    }

    @Test
    void reopensRejectedBindingAsPending() {
        ElderFamilyBinding binding =
                existing(
                        ElderFamilyBinding.Status.REJECTED
                );

        ElderFamilyBinding reopened =
                binding.requestAgain(
                        ElderFamilyBinding.Relationship.GUARDIAN,
                        false,
                        ElderFamilyBinding.AccessScope.FULL
                );

        assertThat(reopened.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );
    }

    @Test
    void refusesToRequestAgainWhenBindingIsStillPending() {
        ElderFamilyBinding binding =
                existing(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        assertThatThrownBy(() ->
                binding.requestAgain(
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
    void identifiesOwningElder() {
        ElderFamilyBinding binding =
                existing(
                        ElderFamilyBinding.Status.ACTIVE
                );

        assertThat(
                binding.belongsToElder(1L)
        ).isTrue();

        assertThat(
                binding.belongsToElder(999L)
        ).isFalse();
    }

    private ElderFamilyBinding existing(
            ElderFamilyBinding.Status status) {

        return new ElderFamilyBinding(
                10L,
                1L,
                3L,
                ElderFamilyBinding.Relationship.SON,
                false,
                ElderFamilyBinding.AccessScope.FULL,
                status,
                status == ElderFamilyBinding.Status.ACTIVE
                        ? LocalDateTime.of(
                                2026,
                                9,
                                21,
                                10,
                                0
                        )
                        : null,
                null,
                LocalDateTime.of(
                        2026,
                        9,
                        21,
                        9,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        9,
                        21,
                        9,
                        0
                )
        );
    }
}