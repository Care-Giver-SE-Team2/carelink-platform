package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringRunJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class RosteringRunMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		RosteringRunJpaEntity entity = new RosteringRunJpaEntity();
		entity.setId(1L);
		entity.setTriggerType(RosteringRunJpaEntity.TriggerType.NEW_VISIT);
		entity.setAbsenceId(3L);
		entity.setObjective(RosteringRunJpaEntity.Objective.CONTINUITY);
		entity.setRequestedByUserId(5L);
		entity.setStatus(RosteringRunJpaEntity.Status.PROPOSED);
		entity.setVisitsTotal(7);
		entity.setVisitsCovered(8);
		entity.setContinuityKept(9);
		entity.setAddedTravelKm(new BigDecimal("10.5"));
		entity.setRanAt(LocalDateTime.of(2026, 9, 6, 10, 11));
		entity.setCommittedAt(LocalDateTime.of(2026, 9, 6, 10, 12));

		RosteringRun domain = RosteringRunMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.triggerType().name()).isEqualTo(entity.getTriggerType().name());
		assertThat(domain.absenceId()).isEqualTo(entity.getAbsenceId());
		assertThat(domain.objective().name()).isEqualTo(entity.getObjective().name());
		assertThat(domain.requestedByUserId()).isEqualTo(entity.getRequestedByUserId());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.visitsTotal()).isEqualTo(entity.getVisitsTotal());
		assertThat(domain.visitsCovered()).isEqualTo(entity.getVisitsCovered());
		assertThat(domain.continuityKept()).isEqualTo(entity.getContinuityKept());
		assertThat(domain.addedTravelKm()).isEqualTo(entity.getAddedTravelKm());
		assertThat(domain.ranAt()).isEqualTo(entity.getRanAt());
		assertThat(domain.committedAt()).isEqualTo(entity.getCommittedAt());

		RosteringRunJpaEntity back = RosteringRunMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getTriggerType()).isEqualTo(entity.getTriggerType());
		assertThat(back.getAbsenceId()).isEqualTo(entity.getAbsenceId());
		assertThat(back.getObjective()).isEqualTo(entity.getObjective());
		assertThat(back.getRequestedByUserId()).isEqualTo(entity.getRequestedByUserId());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getVisitsTotal()).isEqualTo(entity.getVisitsTotal());
		assertThat(back.getVisitsCovered()).isEqualTo(entity.getVisitsCovered());
		assertThat(back.getContinuityKept()).isEqualTo(entity.getContinuityKept());
		assertThat(back.getAddedTravelKm()).isEqualTo(entity.getAddedTravelKm());
		assertThat(back.getRanAt()).isEqualTo(entity.getRanAt());
		assertThat(back.getCommittedAt()).isEqualTo(entity.getCommittedAt());
	}
}
