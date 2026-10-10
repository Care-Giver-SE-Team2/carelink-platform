package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitJpaRepository;

class VisitRepositoryAdapterTest {

    private final VisitJpaRepository jpa =
            mock(
                    VisitJpaRepository.class
            );

    private final VisitRepositoryAdapter adapter =
            new VisitRepositoryAdapter(
                    jpa
            );

    @Test
    void findByIdMapsTheEntityToTheDomainModel() {
        VisitJpaEntity entity =
                new VisitJpaEntity();

        entity.setId(7L);

        when(
                jpa.findById(7L)
        ).thenReturn(
                Optional.of(entity)
        );

        Optional<Visit> found =
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
    void findsCompletedVisitsForElder() {
        VisitJpaEntity first =
                new VisitJpaEntity();

        first.setId(7L);
        first.setElderId(1L);
        first.setStatus(
                VisitJpaEntity.Status.COMPLETED
        );

        VisitJpaEntity second =
                new VisitJpaEntity();

        second.setId(8L);
        second.setElderId(1L);
        second.setStatus(
                VisitJpaEntity.Status.COMPLETED
        );

        when(
                jpa.findByElderIdAndStatusOrderByScheduledStartDesc(
                        1L,
                        VisitJpaEntity.Status.COMPLETED
                )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        List<Visit> result =
                adapter.findCompletedByElderId(
                        1L
                );

        assertThat(result)
                .extracting(
                        Visit::id
                )
                .containsExactly(
                        7L,
                        8L
                );

        verify(jpa)
                .findByElderIdAndStatusOrderByScheduledStartDesc(
                        1L,
                        VisitJpaEntity.Status.COMPLETED
                );
    }

    @Test
    void saveGoesThroughSpringDataAndComesBackAsDomain() {
        VisitJpaEntity entity =
                new VisitJpaEntity();

        entity.setId(7L);

        when(
                jpa.save(
                        any(
                                VisitJpaEntity.class
                        )
                )
        ).thenReturn(entity);

        Visit saved =
                adapter.save(
                        VisitMapper.toDomain(
                                entity
                        )
                );

        assertThat(saved)
                .isNotNull();
    }

    @Test
    void findsAssignedVisitsWithoutChangingTheElderConfirmationQuery() {
        var from = java.time.LocalDate.of(2026, 9, 24).atStartOfDay();
        var until = from.plusDays(7);
        var entity = new VisitJpaEntity();
        entity.setId(9L);
        entity.setCaregiverId(2L);
        when(jpa.findByCaregiverIdAndScheduledStartGreaterThanEqualAndScheduledStartLessThanOrderByScheduledStartAscIdAsc(
                2L, from, until)).thenReturn(List.of(entity));

        assertThat(adapter.findAssigned(2L, from, until)).extracting(Visit::id).containsExactly(9L);
        verify(jpa).findByCaregiverIdAndScheduledStartGreaterThanEqualAndScheduledStartLessThanOrderByScheduledStartAscIdAsc(
                2L, from, until);
    }
}
