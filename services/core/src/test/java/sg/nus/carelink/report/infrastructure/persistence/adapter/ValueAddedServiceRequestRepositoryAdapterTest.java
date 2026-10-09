package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceRequestJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ValueAddedServiceRequestJpaRepository;

class ValueAddedServiceRequestRepositoryAdapterTest {

    private final ValueAddedServiceRequestJpaRepository jpa =
            mock(ValueAddedServiceRequestJpaRepository.class);

    private final ValueAddedServiceRequestRepositoryAdapter adapter =
            new ValueAddedServiceRequestRepositoryAdapter(jpa);

    @Test
    void findByIdMapsTheEntityToTheDomainModel() {
        ValueAddedServiceRequestJpaEntity entity =
                pendingEntity(7L);

        when(jpa.findById(7L))
                .thenReturn(Optional.of(entity));

        Optional<ValueAddedServiceRequest> found =
                adapter.findById(7L);

        assertThat(found).isPresent();
        assertThat(found.get().id())
                .isEqualTo(7L);

        assertThat(found.get().status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.PENDING_APPROVAL
                );
    }

    @Test
    void findByIdIsEmptyWhenThereIsNoRow() {
        when(jpa.findById(any()))
                .thenReturn(Optional.empty());

        assertThat(adapter.findById(7L))
                .isEmpty();
    }

    @Test
    void findsRequestsForElderInRepositoryOrder() {
        ValueAddedServiceRequestJpaEntity first =
                pendingEntity(9L);

        ValueAddedServiceRequestJpaEntity second =
                pendingEntity(8L);

        when(
                jpa.findByElderIdOrderByCreatedAtDesc(1L)
        ).thenReturn(
                List.of(first, second)
        );

        assertThat(
                adapter.findByElderId(1L)
        )
                .extracting(
                        ValueAddedServiceRequest::id
                )
                .containsExactly(
                        9L,
                        8L
                );

        verify(jpa)
                .findByElderIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void saveGoesThroughSpringDataAndComesBackAsDomain() {
        ValueAddedServiceRequestJpaEntity entity =
                pendingEntity(7L);

        when(
                jpa.save(
                        any(ValueAddedServiceRequestJpaEntity.class)
                )
        ).thenReturn(entity);

        ValueAddedServiceRequest saved =
                adapter.save(
                        ValueAddedServiceRequestMapper
                                .toDomain(entity)
                );

        assertThat(saved.id())
                .isEqualTo(7L);

        assertThat(saved.elderId())
                .isEqualTo(1L);

        assertThat(saved.valueAddedServiceId())
                .isEqualTo(2L);
    }

    private ValueAddedServiceRequestJpaEntity pendingEntity(
            Long id) {

        ValueAddedServiceRequestJpaEntity entity =
                new ValueAddedServiceRequestJpaEntity();

        entity.setId(id);
        entity.setElderId(1L);
        entity.setValueAddedServiceId(2L);

        entity.setRequestedSchedule(
                LocalDateTime.of(
                        2026,
                        10,
                        10,
                        10,
                        0
                )
        );

        entity.setSpecialInstructions(
                "Need assistance"
        );

        entity.setStatus(
                ValueAddedServiceRequestJpaEntity
                        .Status
                        .PENDING_APPROVAL
        );

        return entity;
    }
}