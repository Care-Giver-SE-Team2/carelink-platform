package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.careplan.application.CarePlanLookup;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.profile.domain.model.Elder;

/**
 * Verifies scoped elder queries, plan metadata and failure propagation.
 *
 * @author Wang Zhili
 */
class FamilyElderQueryServiceTest {

	private static final LocalDate SINGAPORE_TODAY = LocalDate.of(2026, 9, 23);

	private final FamilyReadAudit audit = mock(FamilyReadAudit.class);
	private final FamilyAccessQuery access = mock(FamilyAccessQuery.class);
	private final InMemoryElderRepository elders = spy(new InMemoryElderRepository());
	private final CarePlanLookup carePlans = mock(CarePlanLookup.class);
	private final FamilyElderQueryService service = new FamilyElderQueryService(
			access, new ProfileService(elders, carePlans,
					new InMemoryPrimaryCaregiverAssignmentRepository(), new InMemoryCaregiverRepository()),
			Clock.fixed(Instant.parse("2026-09-22T16:30:00Z"), ZoneOffset.UTC), audit);

	@BeforeEach
	void executeAuditedQueries() {
		when(audit.read(anyString(), any(), nullable(Long.class), anyString(), any()))
				.thenAnswer(call -> call.<Supplier<?>>getArgument(4).get());
	}

	@Test
	void loadsOnlyReadableProfilesAndTheirPlanMetadataUsingTheSingaporeDate() {
		Elder first = saveElder("First elder");
		Elder second = saveElder("Second elder");
		Elder unrelated = saveElder("Unrelated elder");
		Set<Long> readableIds = Set.of(second.id(), first.id());
		when(access.readableElderIds("family-a")).thenReturn(readableIds);
		when(carePlans.findLatestByElderId(first.id())).thenReturn(Optional.of(new CarePlan(
				101L, first.id(), 1L, null, 3, CarePlan.Status.PUBLISHED, BigDecimal.TEN,
				null, null, null)));
		when(carePlans.findNextVisitDate(first.id(), SINGAPORE_TODAY))
				.thenReturn(Optional.of(SINGAPORE_TODAY.plusDays(1)));

		assertThat(service.listForFamily("family-a")).containsExactly(
				new ElderSummary(first, "published", 3, SINGAPORE_TODAY.plusDays(1)),
				new ElderSummary(second, "none", null, null));
		verify(elders).findByIds(readableIds);
		verify(elders, never()).findAll();
		verify(carePlans, never()).findLatestByElderId(unrelated.id());
		verify(carePlans).findNextVisitDate(second.id(), SINGAPORE_TODAY);
	}

	@Test
	void returnsAnEmptyListWithoutLoadingProfilesWhenNoBindingGrantsAccess() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of());

		assertThat(service.listForFamily("family-a")).isEmpty();
		verifyNoInteractions(elders, carePlans);
	}

	@Test
	void rejectsAnUnavailableFamilyBeforeQueryingProfiles() {
		when(access.readableElderIds("family-a"))
				.thenThrow(new AccessDeniedException("A family profile is required"));

		assertThatThrownBy(() -> service.listForFamily("family-a"))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(elders, carePlans);
	}

	@Test
	void doesNotTurnADatabaseFailureIntoAnEmptyElderList() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(1L));
		doThrow(new DataAccessResourceFailureException("Database unavailable")).when(elders).findByIds(any());

		assertThatThrownBy(() -> service.listForFamily("family-a"))
				.isInstanceOf(DataAccessResourceFailureException.class);
		verifyNoInteractions(carePlans);
	}

	private Elder saveElder(String name) {
		return elders.save(new Elder(null, null, name, null, null, null, null, null, null,
				null, null, null, Elder.ContinuityPreference.PREFERRED, null, null, null));
	}
}
