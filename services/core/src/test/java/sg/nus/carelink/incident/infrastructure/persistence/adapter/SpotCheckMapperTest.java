package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.infrastructure.persistence.entity.SpotCheckJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class SpotCheckMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		SpotCheckJpaEntity entity = new SpotCheckJpaEntity();
		entity.setId(1L);
		entity.setElderId(2L);
		entity.setCaregiverId(3L);
		entity.setVisitId(4L);
		entity.setRaisedByUserId(5L);
		entity.setApprovingFamilyMemberId(6L);
		entity.setProposedTime(LocalDateTime.of(2026, 9, 6, 10, 7));
		entity.setReason("v8");
		entity.setApprovalStatus(SpotCheckJpaEntity.ApprovalStatus.PENDING_APPROVAL);
		entity.setDecidedAt(LocalDateTime.of(2026, 9, 6, 10, 10));
		entity.setFinding("v11");
		entity.setCaregiverResponse("v12");
		entity.setCheckedAt(LocalDateTime.of(2026, 9, 6, 10, 13));
		entity.setResult(SpotCheckJpaEntity.Result.NEEDS_IMPROVEMENT);
		entity.setOutcome(SpotCheckJpaEntity.Outcome.COMPLETED);
		entity.setClosingReason("v15");
		entity.setIncidentId(16L);

		SpotCheck domain = SpotCheckMapper.toDomain(entity);
		assertThat(domain.result().name()).isEqualTo(entity.getResult().name());
		assertThat(domain.outcome().name()).isEqualTo(entity.getOutcome().name());
		assertThat(domain.closingReason()).isEqualTo(entity.getClosingReason());
		assertThat(domain.incidentId()).isEqualTo(entity.getIncidentId());
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.raisedByUserId()).isEqualTo(entity.getRaisedByUserId());
		assertThat(domain.approvingFamilyMemberId()).isEqualTo(entity.getApprovingFamilyMemberId());
		assertThat(domain.proposedTime()).isEqualTo(entity.getProposedTime());
		assertThat(domain.reason()).isEqualTo(entity.getReason());
		assertThat(domain.approvalStatus().name()).isEqualTo(entity.getApprovalStatus().name());
		assertThat(domain.decidedAt()).isEqualTo(entity.getDecidedAt());
		assertThat(domain.finding()).isEqualTo(entity.getFinding());
		assertThat(domain.caregiverResponse()).isEqualTo(entity.getCaregiverResponse());
		assertThat(domain.checkedAt()).isEqualTo(entity.getCheckedAt());

		SpotCheckJpaEntity back = SpotCheckMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getRaisedByUserId()).isEqualTo(entity.getRaisedByUserId());
		assertThat(back.getApprovingFamilyMemberId()).isEqualTo(entity.getApprovingFamilyMemberId());
		assertThat(back.getProposedTime()).isEqualTo(entity.getProposedTime());
		assertThat(back.getReason()).isEqualTo(entity.getReason());
		assertThat(back.getApprovalStatus()).isEqualTo(entity.getApprovalStatus());
		assertThat(back.getDecidedAt()).isEqualTo(entity.getDecidedAt());
		assertThat(back.getFinding()).isEqualTo(entity.getFinding());
		assertThat(back.getCaregiverResponse()).isEqualTo(entity.getCaregiverResponse());
		assertThat(back.getCheckedAt()).isEqualTo(entity.getCheckedAt());
		assertThat(back.getResult()).isEqualTo(entity.getResult());
		assertThat(back.getOutcome()).isEqualTo(entity.getOutcome());
		assertThat(back.getClosingReason()).isEqualTo(entity.getClosingReason());
		assertThat(back.getIncidentId()).isEqualTo(entity.getIncidentId());
	}
}
