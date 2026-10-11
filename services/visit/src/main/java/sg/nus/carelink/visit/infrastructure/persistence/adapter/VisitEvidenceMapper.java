package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import sg.nus.carelink.visit.domain.model.VisitEvidence;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitEvidenceJpaEntity;

/**
 * JPA entity <-> domain model for visit_evidence, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by VisitEvidenceMapperTest.
 */
final class VisitEvidenceMapper {

	private VisitEvidenceMapper() {
	}

	static VisitEvidence toDomain(VisitEvidenceJpaEntity e) {
		return new VisitEvidence(
				e.getId(),
				e.getVisitId(),
				e.getKind() == null ? null : VisitEvidence.Kind.valueOf(e.getKind().name()),
				e.getReference(),
				e.getVerificationStatus() == null ? null : VisitEvidence.VerificationStatus.valueOf(e.getVerificationStatus().name()),
				e.getCapturedAt());
	}

	static VisitEvidenceJpaEntity toEntity(VisitEvidence d) {
		VisitEvidenceJpaEntity e = new VisitEvidenceJpaEntity();
		e.setId(d.id());
		e.setVisitId(d.visitId());
		e.setKind(d.kind() == null ? null : VisitEvidenceJpaEntity.Kind.valueOf(d.kind().name()));
		e.setReference(d.reference());
		e.setVerificationStatus(d.verificationStatus() == null ? null : VisitEvidenceJpaEntity.VerificationStatus.valueOf(d.verificationStatus().name()));
		e.setCapturedAt(d.capturedAt());
		return e;
	}
}
