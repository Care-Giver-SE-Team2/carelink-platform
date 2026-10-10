package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.visit.domain.model.VisitPage;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;

/**
 * Checks family access before any schedule query and resolves one server read time.
 *
 * @author Wang Zhili
 */
class FamilyVisitQueryServiceTest {

	private final FamilyReadAudit audit = mock(FamilyReadAudit.class);
	private final FamilyAccessQuery access = mock(FamilyAccessQuery.class);
	private final VisitScheduleQuery visits = mock(VisitScheduleQuery.class);
	private final FamilyVisitQueryService service = new FamilyVisitQueryService(access, visits,
			Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC), audit);

	@BeforeEach
	void executeAuditedQueries() {
		when(audit.read(anyString(), any(), nullable(Long.class), anyString(), any()))
				.thenAnswer(call -> call.<Supplier<?>>getArgument(4).get());
	}

	@Test
	void defaultsToTheCurrentSingaporeWeekWithinAllReadableElders() {
		Set<Long> elderIds = Set.of(101L, 102L);
		var filter = filter(null, null);
		var dates = new VisitScheduleFilter.DateRange(LocalDate.of(2026, 9, 28).atStartOfDay(),
				LocalDate.of(2026, 10, 5).atStartOfDay());
		var page = new VisitPage(List.of(), 0, 20, 0);
		when(access.readableElderIds("family-a")).thenReturn(elderIds);
		when(visits.findForElders(elderIds, filter, dates)).thenReturn(page);

		var result = service.listMine("family-a", filter);

		assertThat(result.visits()).isEqualTo(page);
		assertThat(result.asOf()).isEqualTo("2026-09-28T00:30:00+08:00");
		verify(visits, never()).hasAssignedVisit(anySet(), anyLong());
	}

	@Test
	void authorizesTheCaregiverAcrossReadableEldersThenNarrowsTheScheduleToTheSelectedElder() {
		Set<Long> elderIds = Set.of(101L, 102L);
		var filter = filter(101L, 201L);
		when(access.readableElderIds("family-a")).thenReturn(elderIds);
		when(visits.hasAssignedVisit(elderIds, 201L)).thenReturn(true);
		when(visits.findForElders(eq(Set.of(101L)), eq(filter), any()))
				.thenReturn(new VisitPage(List.of(), 0, 20, 0));

		assertThat(service.listMine("family-a", filter).visits().items()).isEmpty();
		verify(visits).hasAssignedVisit(elderIds, 201L);
		verify(visits).findForElders(eq(Set.of(101L)), eq(filter), any());
	}

	@Test
	void returnsAnEmptyPageWithoutQueryingVisitsWhenNoElderIsReadable() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of());

		assertThat(service.listMine("family-a", filter(null, null)).visits())
				.isEqualTo(new VisitPage(List.of(), 0, 20, 0));
		verifyNoInteractions(visits);
	}

	@Test
	void explicitlyRequestedEldersOutsideTheReadableSetAreForbidden() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(101L));
		var filter = filter(102L, null);

		assertThatThrownBy(() -> service.listMine("family-a", filter)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(visits);
	}

	@Test
	void anUnrelatedCaregiverIsForbiddenEvenWhenTheQueryWouldBeEmpty() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of());
		var filter = filter(null, 201L);

		assertThatThrownBy(() -> service.listMine("family-a", filter)).isInstanceOf(AccessDeniedException.class);
		verify(visits, never()).findForElders(anySet(), any(), any());
	}

	@Test
	void identityDenialStopsBeforeTheScheduleQuery() {
		when(access.readableElderIds("family-a")).thenThrow(new AccessDeniedException("Family profile unavailable"));
		var filter = filter(null, null);

		assertThatThrownBy(() -> service.listMine("family-a", filter)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(visits);
	}

	@Test
	void databaseFailureDoesNotBecomeAnEmptyPage() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(101L));
		when(visits.findForElders(anySet(), any(), any()))
				.thenThrow(new DataAccessResourceFailureException("Database unavailable"));
		var filter = filter(null, null);

		assertThatThrownBy(() -> service.listMine("family-a", filter))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	private static VisitScheduleFilter filter(Long elderId, Long caregiverId) {
		return new VisitScheduleFilter(elderId, caregiverId, null, null, null, 0, 20);
	}
}
