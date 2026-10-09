package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ValueAddedServiceJpaRepository;

class ValueAddedServiceRepositoryAdapterTest {

    private final ValueAddedServiceJpaRepository jpa =
            mock(ValueAddedServiceJpaRepository.class);

    private final ValueAddedServiceRepositoryAdapter adapter =
            new ValueAddedServiceRepositoryAdapter(jpa);

    @Test
    void findByIdMapsTheEntityToTheDomainModel() {
        ValueAddedServiceJpaEntity entity =
                availableEntity(7L, "Hospital escort");

        when(jpa.findById(7L))
                .thenReturn(Optional.of(entity));

        Optional<ValueAddedService> found =
                adapter.findById(7L);

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(7L);
        assertThat(found.get().name())
                .isEqualTo("Hospital escort");
    }

    @Test
    void findByIdIsEmptyWhenThereIsNoRow() {
        when(jpa.findById(any()))
                .thenReturn(Optional.empty());

        assertThat(adapter.findById(7L))
                .isEmpty();
    }

    @Test
    void findsOnlyAvailableServicesInRepositoryOrder() {
        ValueAddedServiceJpaEntity first =
                availableEntity(
                        1L,
                        "Companionship"
                );

        ValueAddedServiceJpaEntity second =
                availableEntity(
                        2L,
                        "Hospital escort"
                );

        when(
                jpa.findByStatusOrderByNameAsc(
                        ValueAddedServiceJpaEntity.Status.AVAILABLE
                )
        ).thenReturn(
                List.of(first, second)
        );

        assertThat(adapter.findAvailable())
                .extracting(ValueAddedService::name)
                .containsExactly(
                        "Companionship",
                        "Hospital escort"
                );

        verify(jpa)
                .findByStatusOrderByNameAsc(
                        ValueAddedServiceJpaEntity.Status.AVAILABLE
                );
    }

    @Test
    void saveGoesThroughSpringDataAndComesBackAsDomain() {
        ValueAddedServiceJpaEntity entity =
                availableEntity(
                        7L,
                        "Hospital escort"
                );

        when(
                jpa.save(
                        any(ValueAddedServiceJpaEntity.class)
                )
        ).thenReturn(entity);

        ValueAddedService saved =
                adapter.save(
                        ValueAddedServiceMapper.toDomain(entity)
                );

        assertThat(saved.id()).isEqualTo(7L);
        assertThat(saved.available()).isTrue();
    }

    private ValueAddedServiceJpaEntity availableEntity(
            Long id,
            String name) {

        ValueAddedServiceJpaEntity entity =
                new ValueAddedServiceJpaEntity();

        entity.setId(id);
        entity.setName(name);
        entity.setDescription(
                "Optional care service"
        );
        entity.setStatus(
                ValueAddedServiceJpaEntity.Status.AVAILABLE
        );

        return entity;
    }
}