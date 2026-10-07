package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.PageSlice;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class IncidentRepositoryAdapterTest {

	private final IncidentJpaRepository jpa = mock(IncidentJpaRepository.class);
	private final IncidentRepositoryAdapter adapter = new IncidentRepositoryAdapter(jpa);

	/**
	 * A row as the database would really hand it back. Every enum column is NOT NULL in V2,
	 * so a half-built entity is not a case the adapter has to survive - and the domain model
	 * refuses one on purpose.
	 */
	private static IncidentJpaEntity row(Long id) {
		IncidentJpaEntity entity = new IncidentJpaEntity();
		entity.setId(id);
		entity.setElderId(7L);
		entity.setSource(IncidentJpaEntity.Source.ELDER_SOS);
		entity.setCategory(IncidentJpaEntity.Category.SOS);
		entity.setSeverity(IncidentJpaEntity.Severity.HIGH);
		entity.setStatus(IncidentJpaEntity.Status.OPEN);
		entity.setReportedAt(LocalDateTime.of(2026, 9, 16, 14, 30));
		return entity;
	}

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		when(jpa.findById(7L)).thenReturn(Optional.of(row(7L)));

		Optional<Incident> found = adapter.findById(7L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(7L);
		assertThat(found.get().severity()).isEqualTo(Incident.Severity.HIGH);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		IncidentJpaEntity entity = row(7L);
		when(jpa.save(any(IncidentJpaEntity.class))).thenReturn(entity);

		Incident saved = adapter.save(IncidentMapper.toDomain(entity));

		assertThat(saved).isNotNull();
		assertThat(saved.id()).isEqualTo(7L);
	}

	@Test
	void theOverdueQueryAsksOnlyForIncidentsNobodyHasTakenOver() {
		LocalDateTime deadline = LocalDateTime.of(2026, 9, 16, 14, 36);
		when(jpa.findByStatusInAndRespondByNotNullAndRespondByLessThanEqualOrderByRespondByAsc(
				anyCollection(), eq(deadline)))
				.thenReturn(List.of(row(7L)));

		List<Incident> overdue = adapter.findAwaitingTakeOverPastDeadline(deadline);

		assertThat(overdue).hasSize(1);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<IncidentJpaEntity.Status>> statuses =
				ArgumentCaptor.forClass(Collection.class);
		verify(jpa).findByStatusInAndRespondByNotNullAndRespondByLessThanEqualOrderByRespondByAsc(
				statuses.capture(), eq(deadline));
		assertThat(statuses.getValue())
				.containsExactlyInAnyOrder(IncidentJpaEntity.Status.OPEN, IncidentJpaEntity.Status.ACKNOWLEDGED);
	}

	@Test
	void incidentsCanBeListedForOneElder() {
		when(jpa.findByElderIdOrderByReportedAtDesc(7L)).thenReturn(List.of(row(7L), row(8L)));

		assertThat(adapter.findByElder(7L)).hasSize(2);
	}

	/**
	 * The states arrive as domain values and have to leave as the persistence enum, and the
	 * page has to come back as the domain's own record - the port may not mention Spring
	 * Data at all.
	 */
	@Test
	void theQueueTranslatesTheDomainStatesAndComesBackAsADomainPage() {
		when(jpa.findQueue(anyCollection(), anyCollection(), any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(row(7L)), PageRequest.of(0, 1), 3));

		PageSlice<Incident> queue = adapter.findQueue(
				Set.of(Incident.Status.OPEN, Incident.Status.ACKNOWLEDGED), null, null, 0, 1);

		assertThat(queue.items()).hasSize(1);
		assertThat(queue.page()).isZero();
		assertThat(queue.size()).isEqualTo(1);
		assertThat(queue.totalElements())
				.as("the count is the whole queue, not the rows on this page")
				.isEqualTo(3);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<IncidentJpaEntity.Status>> statuses =
				ArgumentCaptor.forClass(Collection.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<IncidentJpaEntity.Severity>> severities =
				ArgumentCaptor.forClass(Collection.class);
		verify(jpa).findQueue(
				statuses.capture(), severities.capture(), isNull(), any(), any(Pageable.class));

		assertThat(statuses.getValue()).containsExactlyInAnyOrder(
				IncidentJpaEntity.Status.OPEN, IncidentJpaEntity.Status.ACKNOWLEDGED);
		assertThat(severities.getValue())
				.as("no severity filter asks for all three, never for null")
				.containsExactlyInAnyOrder(IncidentJpaEntity.Severity.values());
	}

	/**
	 * The order is the query's own, three tiers deep, and only the query can express it.
	 * What the adapter owes it is which state sits on top - the chain ran out and nobody is
	 * answerable, UC-MG05 3b - and a page request with no sort of its own, which would
	 * otherwise be appended after the query's order and quietly change it.
	 */
	@Test
	void theQueuePinsTheIncidentsTheChainRanOutOnAndLeavesTheOrderToTheQuery() {
		when(jpa.findQueue(anyCollection(), anyCollection(), any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		adapter.findQueue(Set.of(Incident.Status.OPEN), null, null, 1, 5);

		ArgumentCaptor<IncidentJpaEntity.Status> pinned =
				ArgumentCaptor.forClass(IncidentJpaEntity.Status.class);
		ArgumentCaptor<Pageable> request = ArgumentCaptor.forClass(Pageable.class);
		verify(jpa).findQueue(anyCollection(), anyCollection(), any(), pinned.capture(), request.capture());

		assertThat(pinned.getValue()).isEqualTo(IncidentJpaEntity.Status.UNRESOLVED_ESCALATED);
		assertThat(request.getValue().getPageNumber()).isEqualTo(1);
		assertThat(request.getValue().getPageSize()).isEqualTo(5);
		assertThat(request.getValue().getSort().isUnsorted()).isTrue();
	}

	@Test
	void askingTheQueueForOneElderPassesTheElderAndTheSeverityThrough() {
		when(jpa.findQueue(anyCollection(), anyCollection(), eq(7L), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(row(7L))));

		PageSlice<Incident> queue = adapter.findQueue(
				Set.of(Incident.Status.OPEN), Incident.Severity.HIGH, 7L, 0, 20);

		assertThat(queue.items()).hasSize(1);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<IncidentJpaEntity.Severity>> severities =
				ArgumentCaptor.forClass(Collection.class);
		verify(jpa).findQueue(anyCollection(), severities.capture(), eq(7L), any(), any(Pageable.class));
		assertThat(severities.getValue()).containsExactly(IncidentJpaEntity.Severity.HIGH);
	}
}
