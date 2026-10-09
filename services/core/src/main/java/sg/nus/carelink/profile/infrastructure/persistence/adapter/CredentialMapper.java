package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialJpaEntity;

/**
 * JPA entity <-> domain model for credential, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CredentialMapperTest.
 */
final class CredentialMapper {

	private CredentialMapper() {
	}

	static Credential toDomain(CredentialJpaEntity e) {
		return new Credential(
				e.getId(),
				e.getCaregiverId(),
				e.getCredentialTypeId(),
				e.getReviewedByUserId(),
				e.getCertificateNo(),
				e.getIssuingBody(),
				e.getValidFrom(),
				e.getExpiryDate(),
				e.getStatus() == null ? null : Credential.Status.valueOf(e.getStatus().name()),
				e.getCreatedAt(),
				e.getUpdatedAt(),
				e.getRenewsCredentialId(),
				e.getReviewNote(),
				e.getReviewedAt());
	}

	static CredentialJpaEntity toEntity(Credential d) {
		CredentialJpaEntity e = new CredentialJpaEntity();
		e.setId(d.id());
		e.setCaregiverId(d.caregiverId());
		e.setCredentialTypeId(d.credentialTypeId());
		e.setReviewedByUserId(d.reviewedByUserId());
		e.setCertificateNo(d.certificateNo());
		e.setIssuingBody(d.issuingBody());
		e.setValidFrom(d.validFrom());
		e.setExpiryDate(d.expiryDate());
		e.setStatus(d.status() == null ? null : CredentialJpaEntity.Status.valueOf(d.status().name()));
		e.setRenewsCredentialId(d.renewsCredentialId());
		e.setReviewNote(d.reviewNote());
		e.setReviewedAt(d.reviewedAt());
		return e;
	}
}
