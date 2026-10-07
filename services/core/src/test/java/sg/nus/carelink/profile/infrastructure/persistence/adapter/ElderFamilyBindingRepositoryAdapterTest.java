package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderFamilyBindingJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.ElderFamilyBindingJpaRepository;

/**
 * The adapter delegates to Spring Data and maps at the persistence boundary.
 */
class ElderFamilyBindingRepositoryAdapterTest {

    private final ElderFamilyBindingJpaRepository jpa =
            mock(
                    ElderFamilyBindingJpaRepository.class
            );

    private final EntityManager entityManager =
            mock(EntityManager.class);

    private final ElderFamilyBindingRepositoryAdapter adapter =
            new ElderFamilyBindingRepositoryAdapter(
                    jpa,
                    entityManager
            );

    @Test
    void findByIdMapsTheEntityToTheDomainModel() {
        ElderFamilyBindingJpaEntity entity =
                entity();

        when(
                jpa.findById(10L)
        ).thenReturn(
                Optional.of(entity)
        );

        Optional<ElderFamilyBinding> found =
                adapter.findById(10L);

        assertThat(found)
                .isPresent();

        assertThat(found.get().id())
                .isEqualTo(10L);

        assertThat(found.get().elderId())
                .isEqualTo(1L);

        assertThat(found.get().familyMemberId())
                .isEqualTo(3L);

        assertThat(found.get().relationship())
                .isEqualTo(
                        ElderFamilyBinding.Relationship.SON
                );

        assertThat(found.get().isPrimaryContact())
                .isTrue();

        assertThat(found.get().accessScope())
                .isEqualTo(
                        ElderFamilyBinding.AccessScope.FULL
                );

        assertThat(found.get().status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        verify(jpa)
                .findById(10L);
    }

    @Test
    void findByIdIsEmptyWhenThereIsNoRow() {
        when(
                jpa.findById(999L)
        ).thenReturn(
                Optional.empty()
        );

        assertThat(
                adapter.findById(999L)
        ).isEmpty();

        verify(jpa)
                .findById(999L);
    }

    @Test
    void listsBindingsForElder() {
        ElderFamilyBindingJpaEntity first =
                entity();

        ElderFamilyBindingJpaEntity second =
                entity();

        second.setId(11L);
        second.setFamilyMemberId(4L);

        second.setRelationship(
                ElderFamilyBindingJpaEntity.Relationship.DAUGHTER
        );

        when(
                jpa.findByElderIdOrderByCreatedAtDesc(
                        1L
                )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        List<ElderFamilyBinding> result =
                adapter.findByElderId(1L);

        assertThat(result)
                .hasSize(2);

        assertThat(result)
                .extracting(
                        ElderFamilyBinding::id
                )
                .containsExactly(
                        10L,
                        11L
                );

        assertThat(
                result.get(1).relationship()
        ).isEqualTo(
                ElderFamilyBinding.Relationship.DAUGHTER
        );

        verify(jpa)
                .findByElderIdOrderByCreatedAtDesc(
                        1L
                );
    }

    @Test
    void returnsEmptyListWhenElderHasNoBindings() {
        when(
                jpa.findByElderIdOrderByCreatedAtDesc(
                        999L
                )
        ).thenReturn(
                List.of()
        );

        assertThat(
                adapter.findByElderId(999L)
        ).isEmpty();

        verify(jpa)
                .findByElderIdOrderByCreatedAtDesc(
                        999L
                );
    }

    @Test
    void findsBindingByElderAndFamilyMember() {
        ElderFamilyBindingJpaEntity entity =
                entity();

        when(
                jpa.findByElderIdAndFamilyMemberId(
                        1L,
                        3L
                )
        ).thenReturn(
                Optional.of(entity)
        );

        Optional<ElderFamilyBinding> found =
                adapter
                        .findByElderIdAndFamilyMemberId(
                                1L,
                                3L
                        );

        assertThat(found)
                .isPresent();

        assertThat(found.get().id())
                .isEqualTo(10L);

        assertThat(found.get().elderId())
                .isEqualTo(1L);

        assertThat(found.get().familyMemberId())
                .isEqualTo(3L);

        verify(jpa)
                .findByElderIdAndFamilyMemberId(
                        1L,
                        3L
                );
    }

    @Test
    void findByElderAndFamilyMemberIsEmptyWhenPairDoesNotExist() {
        when(
                jpa.findByElderIdAndFamilyMemberId(
                        1L,
                        999L
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThat(
                adapter
                        .findByElderIdAndFamilyMemberId(
                                1L,
                                999L
                        )
        ).isEmpty();

        verify(jpa)
                .findByElderIdAndFamilyMemberId(
                        1L,
                        999L
                );
    }

    @Test
    void saveFlushesRefreshesAndReturnsDatabaseGeneratedTimestamps() {
        ElderFamilyBindingJpaEntity persisted =
                entity();

        /*
         * Simulate the entity immediately after saveAndFlush().
         *
         * The database-generated timestamp has not yet been copied into our
         * mock object. entityManager.refresh() below simulates that reload.
         */
        when(
                jpa.saveAndFlush(
                        any(
                                ElderFamilyBindingJpaEntity.class
                        )
                )
        ).thenReturn(
                persisted
        );

        LocalDateTime createdAt =
                LocalDateTime.of(
                        2026,
                        9,
                        21,
                        13,
                        27,
                        41
                );

        LocalDateTime updatedAt =
                LocalDateTime.of(
                        2026,
                        9,
                        21,
                        13,
                        30,
                        0
                );

        /*
         * Entity has no setters for database-managed timestamp fields.
         * Mockito's refresh callback therefore replaces the state indirectly
         * by using reflection only inside this unit test.
         */
        doAnswer(invocation -> {
            ElderFamilyBindingJpaEntity refreshed =
                    invocation.getArgument(0);

            setTimestamp(
                    refreshed,
                    "createdAt",
                    createdAt
            );

            setTimestamp(
                    refreshed,
                    "updatedAt",
                    updatedAt
            );

            return null;
        }).when(entityManager)
                .refresh(
                        persisted
                );

        ElderFamilyBinding request =
                ElderFamilyBinding.request(
                        1L,
                        3L,
                        ElderFamilyBinding.Relationship.SON,
                        true,
                        ElderFamilyBinding.AccessScope.FULL
                );

        ElderFamilyBinding saved =
                adapter.save(request);

        assertThat(saved.id())
                .isEqualTo(10L);

        assertThat(saved.elderId())
                .isEqualTo(1L);

        assertThat(saved.familyMemberId())
                .isEqualTo(3L);

        assertThat(saved.status())
                .isEqualTo(
                        ElderFamilyBinding.Status.PENDING_CONFIRMATION
                );

        assertThat(saved.createdAt())
                .isEqualTo(createdAt);

        assertThat(saved.updatedAt())
                .isEqualTo(updatedAt);

        verify(jpa)
                .saveAndFlush(
                        any(
                                ElderFamilyBindingJpaEntity.class
                        )
                );

        verify(entityManager)
                .refresh(persisted);
    }

    private ElderFamilyBindingJpaEntity entity() {
        ElderFamilyBindingJpaEntity entity =
                new ElderFamilyBindingJpaEntity();

        entity.setId(10L);
        entity.setElderId(1L);
        entity.setFamilyMemberId(3L);

        entity.setRelationship(
                ElderFamilyBindingJpaEntity.Relationship.SON
        );

        entity.setIsPrimaryContact(true);

        entity.setAccessScope(
                ElderFamilyBindingJpaEntity.AccessScope.FULL
        );

        entity.setStatus(
                ElderFamilyBindingJpaEntity.Status.PENDING_CONFIRMATION
        );

        return entity;
    }

    private void setTimestamp(
            ElderFamilyBindingJpaEntity entity,
            String fieldName,
            LocalDateTime value) {

        try {
            var field =
                    ElderFamilyBindingJpaEntity.class
                            .getDeclaredField(
                                    fieldName
                            );

            field.setAccessible(true);
            field.set(
                    entity,
                    value
            );
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Unable to prepare timestamp test data",
                    exception
            );
        }
    }
}