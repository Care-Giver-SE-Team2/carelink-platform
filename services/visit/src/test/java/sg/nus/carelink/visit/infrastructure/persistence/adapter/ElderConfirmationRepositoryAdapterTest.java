package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.infrastructure.persistence.entity.ElderConfirmationJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.ElderConfirmationJpaRepository;

class ElderConfirmationRepositoryAdapterTest {

    private final ElderConfirmationJpaRepository jpa =
            mock(
                    ElderConfirmationJpaRepository.class
            );

    private final ElderConfirmationRepositoryAdapter adapter =
            new ElderConfirmationRepositoryAdapter(
                    jpa
            );

    @Test
    void findByIdMapsTheEntityToTheDomainModel() {
        ElderConfirmationJpaEntity entity =
                entity();

        when(
                jpa.findById(7L)
        ).thenReturn(
                Optional.of(entity)
        );

        Optional<ElderConfirmation> found =
                adapter.findById(7L);

        assertThat(found)
                .isPresent();

        assertThat(found.get().id())
                .isEqualTo(7L);
    }

    @Test
    void findByIdIsEmptyWhenThereIsNoRow() {
        when(
                jpa.findById(any())
        ).thenReturn(
                Optional.empty()
        );

        assertThat(
                adapter.findById(7L)
        ).isEmpty();
    }

    @Test
    void findsConfirmationByVisitId() {
        ElderConfirmationJpaEntity entity =
                entity();

        when(
                jpa.findByVisitId(15L)
        ).thenReturn(
                Optional.of(entity)
        );

        Optional<ElderConfirmation> found =
                adapter.findByVisitId(
                        15L
                );

        assertThat(found)
                .isPresent();

        assertThat(found.get().visitId())
                .isEqualTo(15L);

        verify(jpa)
                .findByVisitId(15L);
    }

    @Test
    void checksWhetherVisitAlreadyHasConfirmation() {
        when(
                jpa.existsByVisitId(15L)
        ).thenReturn(true);

        assertThat(
                adapter.existsByVisitId(
                        15L
                )
        ).isTrue();

        verify(jpa)
                .existsByVisitId(15L);
    }

    @Test
    void saveGoesThroughSpringDataAndComesBackAsDomain() {
        ElderConfirmationJpaEntity entity =
                entity();

        when(
                jpa.save(
                        any(
                                ElderConfirmationJpaEntity.class
                        )
                )
        ).thenReturn(entity);

        ElderConfirmation saved =
                adapter.save(
                        ElderConfirmationMapper
                                .toDomain(entity)
                );

        assertThat(saved)
                .isNotNull();

        assertThat(saved.visitId())
                .isEqualTo(15L);
    }

    private ElderConfirmationJpaEntity entity() {
        ElderConfirmationJpaEntity entity =
                new ElderConfirmationJpaEntity();

        entity.setId(7L);
        entity.setVisitId(15L);
        entity.setElderId(1L);

        entity.setConfirmationStatus(
                ElderConfirmationJpaEntity
                        .ConfirmationStatus
                        .CONFIRMED
        );

        entity.setRating((byte) 5);
        entity.setComment(
                "Good service"
        );

        entity.setConfirmedAt(
                LocalDateTime.of(
                        2026,
                        9,
                        24,
                        11,
                        0
                )
        );

        return entity;
    }
}