package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitPage;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitJpaRepository;

/**
 * Verifies scoped pagination, stable ordering and visit mapping at the persistence boundary.
 *
 * @author Wang Zhili
 */
class VisitScheduleQueryAdapterTest {

	private static final Set<Long> READABLE_ELDERS = Set.of(101L, 102L);
	private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
	private static final LocalDate LAST_DATE = LocalDate.of(2026, 9, 27);
	private static final VisitScheduleFilter.DateRange DATES = new VisitScheduleFilter.DateRange(
			FIRST_DATE.atStartOfDay(), LAST_DATE.plusDays(1).atStartOfDay());

	private final VisitJpaRepository jpa = mock(VisitJpaRepository.class);
	private final VisitScheduleQueryAdapter adapter = new VisitScheduleQueryAdapter(jpa);

	@Test
	void returnsAnEmptyPageWithoutQueryingWhenNoElderIsReadable() {
		var filter = filter(3, 10);

		VisitPage result = adapter.findForElders(Set.of(), filter, DATES);

		assertThat(result).isEqualTo(new VisitPage(List.of(), 3, 10, 0));
		verifyNoInteractions(jpa);
	}

	@ParameterizedTest
	@ValueSource(ints = {1, Integer.MAX_VALUE})
	void preservesTheFilteredCountWithoutFetchingPagesBeyondTheLastMatch(int page) {
		when(jpa.count(ArgumentMatchers.<Specification<VisitJpaEntity>>any())).thenReturn(3L);

		VisitPage result = adapter.findForElders(READABLE_ELDERS, filter(page, 200), DATES);

		assertThat(result).isEqualTo(new VisitPage(List.of(), page, 200, 3));
		verify(jpa).count(ArgumentMatchers.<Specification<VisitJpaEntity>>any());
		verify(jpa, never()).findAll(ArgumentMatchers.<Specification<VisitJpaEntity>>any(), any(Pageable.class));
	}

	@Test
	void returnsZeroMatchesWithoutFetchingTheFirstPage() {
		when(jpa.count(ArgumentMatchers.<Specification<VisitJpaEntity>>any())).thenReturn(0L);

		VisitPage result = adapter.findForElders(READABLE_ELDERS, filter(0, 20), DATES);

		assertThat(result).isEqualTo(new VisitPage(List.of(), 0, 20, 0));
		verify(jpa, never()).findAll(ArgumentMatchers.<Specification<VisitJpaEntity>>any(), any(Pageable.class));
	}

	@Test
	void countsAndLoadsWithTheSameScopeAndStablePaginationThenMapsSourceFields() {
		LocalDateTime start = LocalDateTime.of(2026, 9, 24, 9, 0);
		VisitJpaEntity assigned = entity(301L, start);
		assigned.setCaregiverId(201L);
		assigned.setServiceType("VITALS");
		assigned.setScheduledEnd(start.plusHours(1));
		assigned.setCheckedInAt(start.plusMinutes(5));
		assigned.setCheckedOutAt(start.plusMinutes(40));
		assigned.setStatus(VisitJpaEntity.Status.COMPLETED);
		VisitJpaEntity unassigned = entity(302L, start);
		var pageRequest = PageRequest.of(1, 2, Sort.by("scheduledStart", "id"));
		when(jpa.count(ArgumentMatchers.<Specification<VisitJpaEntity>>any())).thenReturn(7L);
		when(jpa.findAll(ArgumentMatchers.<Specification<VisitJpaEntity>>any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(assigned, unassigned), pageRequest, 7));

		VisitPage result = adapter.findForElders(READABLE_ELDERS, filter(1, 2), DATES);

		assertThat(result.page()).isEqualTo(1);
		assertThat(result.size()).isEqualTo(2);
		assertThat(result.totalElements()).isEqualTo(7);
		assertThat(result.items()).extracting(Visit::id, Visit::elderId, Visit::caregiverId,
				Visit::scheduledStart, Visit::status)
				.containsExactly(tuple(301L, 101L, 201L, start, Visit.Status.COMPLETED),
						tuple(302L, 101L, null, start, Visit.Status.SCHEDULED));
		assertThat(result.items().getFirst()).satisfies(visit -> {
			assertThat(visit.serviceType()).isEqualTo("VITALS");
			assertThat(visit.scheduledEnd()).isEqualTo(start.plusHours(1));
			assertThat(visit.checkedInAt()).isEqualTo(start.plusMinutes(5));
			assertThat(visit.checkedOutAt()).isEqualTo(start.plusMinutes(40));
		});
		assertThat(result.items().getLast()).satisfies(visit -> {
			assertThat(visit.serviceType()).isNull();
			assertThat(visit.scheduledEnd()).isNull();
			assertThat(visit.checkedInAt()).isNull();
			assertThat(visit.checkedOutAt()).isNull();
		});
		ArgumentCaptor<Specification<VisitJpaEntity>> scope = ArgumentCaptor.captor();
		ArgumentCaptor<Pageable> pagination = ArgumentCaptor.forClass(Pageable.class);
		verify(jpa).count(scope.capture());
		verify(jpa).findAll(same(scope.getValue()), pagination.capture());
		assertThat(pagination.getValue()).isEqualTo(pageRequest);
		assertThat(pagination.getValue().getSort()).containsExactly(
				Sort.Order.asc("scheduledStart"), Sort.Order.asc("id"));
	}

	@Test
	void rejectsCaregiverRelationshipsWithoutQueryingWhenNoElderIsReadable() {
		assertThat(adapter.hasAssignedVisit(Set.of(), 201L)).isFalse();

		verifyNoInteractions(jpa);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void returnsTheScopedCaregiverRelationship(boolean hasRelationship) {
		when(jpa.existsByElderIdInAndCaregiverId(READABLE_ELDERS, 201L)).thenReturn(hasRelationship);

		assertThat(adapter.hasAssignedVisit(READABLE_ELDERS, 201L)).isEqualTo(hasRelationship);

		verify(jpa).existsByElderIdInAndCaregiverId(READABLE_ELDERS, 201L);
	}

	private static VisitScheduleFilter filter(int page, int size) {
		return new VisitScheduleFilter(null, null, FIRST_DATE, LAST_DATE, null, page, size);
	}

	private static VisitJpaEntity entity(Long id, LocalDateTime scheduledStart) {
		var entity = new VisitJpaEntity();
		entity.setId(id);
		entity.setElderId(101L);
		entity.setScheduledStart(scheduledStart);
		return entity;
	}
}
