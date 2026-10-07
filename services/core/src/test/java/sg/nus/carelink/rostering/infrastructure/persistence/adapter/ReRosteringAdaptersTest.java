package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.AbsenceReportJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosterChangeJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateCheckJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringConstraintJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.AbsenceReportJpaRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosterChangeJpaRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringCandidateCheckJpaRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringCandidateJpaRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringConstraintJpaRepository;

/** UC-MG04's storage: roster_change both ways, and the finders added to the scaffolded adapters. */
class ReRosteringAdaptersTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 8, 9, 0);

	@Test
	void rosterChangeSurvivesTheTripColumnByColumn() {
		RosterChangeJpaEntity entity = new RosterChangeJpaEntity();
		entity.setId(1L);
		entity.setAbsenceId(2L);
		entity.setVisitId(3L);
		entity.setElderId(4L);
		entity.setOriginalCaregiverId(5L);
		entity.setVisitStart(NINE);
		entity.setVisitEnd(NINE.plusMinutes(45));
		entity.setRosteringRunId(6L);
		entity.setProposedCaregiverId(7L);
		entity.setStatus(RosterChangeJpaEntity.Status.RESOLVED);
		entity.setOutcome(RosterChangeJpaEntity.Outcome.RESCHEDULED);
		entity.setDecidedBy(RosterChangeJpaEntity.DecidedBy.FAMILY);
		entity.setDecidedByUserId(8L);
		entity.setAssignedCaregiverId(9L);
		entity.setRescheduledVisitId(10L);
		entity.setIncidentId(11L);
		entity.setRespondBy(NINE.minusHours(20));
		entity.setDecidedAt(NINE.minusHours(21));
		entity.setNote("moved");
		entity.setCreatedAt(NINE.minusDays(1));
		entity.setUpdatedAt(NINE.minusHours(21));

		RosterChange domain = RosterChangeMapper.toDomain(entity);
		RosterChangeJpaEntity back = RosterChangeMapper.toEntity(domain);

		assertThat(back).usingRecursiveComparison().isEqualTo(entity);
		assertThat(domain.outcome()).isEqualTo(RosterChange.Outcome.RESCHEDULED);
		assertThat(domain.decidedBy()).isEqualTo(RosterChange.DecidedBy.FAMILY);

		entity.setOutcome(null);
		entity.setDecidedBy(null);
		RosterChange open = RosterChangeMapper.toDomain(entity);
		assertThat(open.outcome()).isNull();
		assertThat(RosterChangeMapper.toEntity(open).getDecidedBy()).isNull();
	}

	@Test
	void rosterChangeAdapterDelegatesEveryFinder() {
		RosterChangeJpaRepository jpa = mock(RosterChangeJpaRepository.class);
		RosterChangeRepositoryAdapter adapter = new RosterChangeRepositoryAdapter(jpa);
		RosterChangeJpaEntity row = new RosterChangeJpaEntity();
		row.setId(1L);
		row.setAbsenceId(2L);
		row.setVisitId(3L);
		row.setStatus(RosterChangeJpaEntity.Status.AWAITING_FAMILY);
		when(jpa.findById(1L)).thenReturn(Optional.of(row));
		when(jpa.lockById(1L)).thenReturn(Optional.of(row));
		when(jpa.save(any(RosterChangeJpaEntity.class))).thenReturn(row);
		when(jpa.findByAbsenceIdOrderByVisitStartAscIdAsc(2L)).thenReturn(List.of(row));
		when(jpa.findByElderIdInOrderByVisitStartAscIdAsc(Set.of(4L))).thenReturn(List.of(row));
		when(jpa.findByStatusAndRespondByLessThanEqualOrderByRespondByAsc(RosterChangeJpaEntity.Status.AWAITING_FAMILY,
				NINE)).thenReturn(List.of(row));

		assertThat(adapter.findById(1L)).isPresent();
		assertThat(adapter.lock(1L)).isPresent();
		assertThat(adapter.save(RosterChangeMapper.toDomain(row)).id()).isEqualTo(1L);
		assertThat(adapter.findByAbsenceId(2L)).hasSize(1);
		assertThat(adapter.findByElderIds(Set.of(4L))).hasSize(1);
		assertThat(adapter.findByElderIds(Set.of())).isEmpty();
		assertThat(adapter.findAwaitingFamilyDueBy(NINE)).hasSize(1);
	}

	@Test
	void absenceFindersPickTheRightQuery() {
		AbsenceReportJpaRepository jpa = mock(AbsenceReportJpaRepository.class);
		AbsenceReportRepositoryAdapter adapter = new AbsenceReportRepositoryAdapter(jpa);
		AbsenceReportJpaEntity row = new AbsenceReportJpaEntity();
		row.setId(1L);
		row.setStatus(AbsenceReportJpaEntity.Status.APPROVED);
		LocalDate day = NINE.toLocalDate();
		when(jpa.findAllByOrderByStartDateDescIdDesc()).thenReturn(List.of(row));
		when(jpa.findByStatusOrderByStartDateDescIdDesc(AbsenceReportJpaEntity.Status.APPROVED)).thenReturn(List.of(row, row));
		when(jpa.findByCaregiverIdOrderByStartDateDescIdDesc(5L)).thenReturn(List.of(row));
		when(jpa.findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(AbsenceReportJpaEntity.Status.APPROVED,
				day.plusDays(1), day)).thenReturn(List.of(row));

		assertThat(adapter.findAll(null)).hasSize(1);
		assertThat(adapter.findAll(AbsenceReport.Status.APPROVED)).hasSize(2);
		assertThat(adapter.findByCaregiverId(5L)).hasSize(1);
		assertThat(adapter.findApprovedOverlapping(day, day.plusDays(1))).extracting(AbsenceReport::id).containsExactly(1L);
	}

	@Test
	void candidatesComeBackSuggestionsFirstThenTheExcluded() {
		RosteringCandidateJpaRepository jpa = mock(RosteringCandidateJpaRepository.class);
		RosteringCandidateRepositoryAdapter adapter = new RosteringCandidateRepositoryAdapter(jpa);
		when(jpa.findByRosteringRunIdAndVisitIdOrderByIdAsc(2L, 30L)).thenReturn(List.of(
				candidate(1L, null, RosteringCandidateJpaEntity.Outcome.EXCLUDED),
				candidate(2L, 2, RosteringCandidateJpaEntity.Outcome.SUGGESTED),
				candidate(3L, 1, RosteringCandidateJpaEntity.Outcome.SELECTED)));

		assertThat(adapter.findByRunAndVisit(2L, 30L)).extracting(RosteringCandidate::id).containsExactly(3L, 2L, 1L);
	}

	@Test
	void checksAndRulesAreReadInBulk() {
		RosteringCandidateCheckJpaRepository checks = mock(RosteringCandidateCheckJpaRepository.class);
		RosteringCandidateCheckRepositoryAdapter checkAdapter = new RosteringCandidateCheckRepositoryAdapter(checks);
		RosteringCandidateCheckJpaEntity check = new RosteringCandidateCheckJpaEntity();
		check.setId(1L);
		check.setResult(RosteringCandidateCheckJpaEntity.Result.PASS);
		when(checks.findByRosteringCandidateIdInOrderByIdAsc(List.of(3L))).thenReturn(List.of(check));

		assertThat(checkAdapter.findByCandidateIds(List.of(3L))).hasSize(1);
		assertThat(checkAdapter.findByCandidateIds(List.of())).isEmpty();
		verify(checks).findByRosteringCandidateIdInOrderByIdAsc(List.of(3L));

		RosteringConstraintJpaRepository rules = mock(RosteringConstraintJpaRepository.class);
		RosteringConstraintJpaEntity rule = new RosteringConstraintJpaEntity();
		rule.setId(1L);
		rule.setCode("NOT_ON_LEAVE");
		rule.setKind(RosteringConstraintJpaEntity.Kind.HARD);
		when(rules.findAll(Sort.by("id"))).thenReturn(List.of(rule));

		assertThat(new RosteringConstraintRepositoryAdapter(rules).findAll()).singleElement()
				.extracting(sg.nus.carelink.rostering.domain.model.RosteringConstraint::code).isEqualTo("NOT_ON_LEAVE");
	}

	private static RosteringCandidateJpaEntity candidate(Long id, Integer rank, RosteringCandidateJpaEntity.Outcome outcome) {
		RosteringCandidateJpaEntity entity = new RosteringCandidateJpaEntity();
		entity.setId(id);
		entity.setRosteringRunId(2L);
		entity.setVisitId(30L);
		entity.setCaregiverId(id + 10);
		entity.setOptionRank(rank);
		entity.setScore(rank == null ? null : BigDecimal.TEN);
		entity.setOutcome(outcome);
		return entity;
	}
}
