package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.ElderJpaRepository;

/**
 * The adapter delegates to Spring Data and maps at the boundary;
 * nothing else.
 */
class ElderRepositoryAdapterTest {

    private final ElderJpaRepository jpa =
            mock(ElderJpaRepository.class);

    private final ElderRepositoryAdapter adapter =
            new ElderRepositoryAdapter(jpa);

    @Test
    void findByIdMapsTheEntityToTheDomainModel() {
        ElderJpaEntity entity =
                new ElderJpaEntity();

        entity.setId(7L);

        when(jpa.findById(7L))
                .thenReturn(Optional.of(entity));

        Optional<Elder> found =
                adapter.findById(7L);

        assertThat(found)
                .isPresent();

        assertThat(found.get().id())
                .isEqualTo(7L);
    }

    @Test
    void findByIdIsEmptyWhenThereIsNoRow() {
        when(jpa.findById(any()))
                .thenReturn(Optional.empty());

        assertThat(adapter.findById(7L))
                .isEmpty();
    }

    @Test
    void findByUserIdMapsTheEntityToTheDomainModel() {
        ElderJpaEntity entity =
                new ElderJpaEntity();

        entity.setId(8L);
        entity.setUserId(27L);
        entity.setFullName("Test Elder");

        when(jpa.findByUserId(27L))
                .thenReturn(Optional.of(entity));

        Optional<Elder> found =
                adapter.findByUserId(27L);

        assertThat(found)
                .isPresent();

        assertThat(found.get().id())
                .isEqualTo(8L);

        assertThat(found.get().userId())
                .isEqualTo(27L);

        assertThat(found.get().fullName())
                .isEqualTo("Test Elder");

        verify(jpa)
                .findByUserId(27L);
    }

    @Test
    void findByUserIdIsEmptyWhenThereIsNoLinkedElder() {
        when(jpa.findByUserId(999L))
                .thenReturn(Optional.empty());

        assertThat(adapter.findByUserId(999L))
                .isEmpty();

        verify(jpa)
                .findByUserId(999L);
    }

    @Test
    void findAllMapsEveryEntityToADomainModel() {
        ElderJpaEntity first = new ElderJpaEntity();
        first.setId(1L);

        ElderJpaEntity second = new ElderJpaEntity();
        second.setId(2L);

        when(jpa.findAll())
                .thenReturn(List.of(first, second));

        List<Elder> found = adapter.findAll();

        assertThat(found)
                .extracting(Elder::id)
                .containsExactly(1L, 2L);

        verify(jpa)
                .findAll();
    }

    @Test
    void mapsTheScopedQueryWithoutIncludingUnrequestedProfiles() {
        ElderJpaEntity entity = new ElderJpaEntity();
        entity.setId(7L);
        entity.setFullName("Bound elder");
        Set<Long> elderIds = Set.of(7L, 99L);
        when(jpa.findByIdInOrderByIdAsc(elderIds)).thenReturn(List.of(entity));

        assertThat(adapter.findByIds(elderIds))
                .extracting(Elder::id, Elder::fullName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(7L, "Bound elder"));
        verify(jpa).findByIdInOrderByIdAsc(elderIds);
    }

    @Test
    void saveGoesThroughSpringDataAndComesBackAsDomain() {
        ElderJpaEntity entity =
                new ElderJpaEntity();

        entity.setId(7L);

        when(jpa.save(any(ElderJpaEntity.class)))
                .thenReturn(entity);

        Elder saved =
                adapter.save(
                        ElderMapper.toDomain(entity)
                );

        assertThat(saved)
                .isNotNull();
    }
}
