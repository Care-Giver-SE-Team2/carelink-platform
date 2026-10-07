package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.infrastructure.persistence.entity.SpotCheckJpaEntity;

/**
 * JPA entity <-> domain model for spot_check, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by SpotCheckMapperTest.
 */
final class SpotCheckMapper {

	private SpotCheckMapper() {
	}

	static SpotCheck toDomain(SpotCheckJpaEntity e) {
		return new SpotCheck(
				e.getId(),
				e.getElderId(),
				e.getCaregiverId(),
				e.getVisitId(),
				e.getRaisedByUserId(),
				e.getApprovingFamilyMemberId(),
				e.getProposedTime(),
				e.getReason(),
				e.getApprovalStatus() == null ? null : SpotCheck.ApprovalStatus.valueOf(e.getApprovalStatus().name()),
				e.getDecidedAt(),
				e.getFinding(),
				e.getCaregiverResponse(),
				e.getCheckedAt(),
				e.getCreatedAt(),
				e.getResult() == null ? null : SpotCheck.Result.valueOf(e.getResult().name()),
				e.getOutcome() == null ? null : SpotCheck.Outcome.valueOf(e.getOutcome().name()),
				e.getClosingReason(),
				e.getIncidentId());
	}

	static SpotCheckJpaEntity toEntity(SpotCheck d) {
		SpotCheckJpaEntity e = new SpotCheckJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setCaregiverId(d.caregiverId());
		e.setVisitId(d.visitId());
		e.setRaisedByUserId(d.raisedByUserId());
		e.setApprovingFamilyMemberId(d.approvingFamilyMemberId());
		e.setProposedTime(d.proposedTime());
		e.setReason(d.reason());
		e.setApprovalStatus(d.approvalStatus() == null ? null : SpotCheckJpaEntity.ApprovalStatus.valueOf(d.approvalStatus().name()));
		e.setDecidedAt(d.decidedAt());
		e.setFinding(d.finding());
		e.setCaregiverResponse(d.caregiverResponse());
		e.setCheckedAt(d.checkedAt());
		e.setResult(d.result() == null ? null : SpotCheckJpaEntity.Result.valueOf(d.result().name()));
		e.setOutcome(d.outcome() == null ? null : SpotCheckJpaEntity.Outcome.valueOf(d.outcome().name()));
		e.setClosingReason(d.closingReason());
		e.setIncidentId(d.incidentId());
		return e;
	}
}
