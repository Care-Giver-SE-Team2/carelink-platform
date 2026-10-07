package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CredentialMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CredentialJpaEntity entity = new CredentialJpaEntity();
		entity.setId(1L);
		entity.setCaregiverId(2L);
		entity.setCredentialTypeId(3L);
		entity.setReviewedByUserId(4L);
		entity.setCertificateNo("v5");
		entity.setIssuingBody("v6");
		entity.setValidFrom(LocalDate.of(2026, 9, 8));
		entity.setExpiryDate(LocalDate.of(2026, 9, 9));
		entity.setStatus(CredentialJpaEntity.Status.SUBMITTED);
		entity.setRenewsCredentialId(12L);
		entity.setReviewNote("v13");
		entity.setReviewedAt(LocalDateTime.of(2026, 9, 14, 10, 30));

		Credential domain = CredentialMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.credentialTypeId()).isEqualTo(entity.getCredentialTypeId());
		assertThat(domain.reviewedByUserId()).isEqualTo(entity.getReviewedByUserId());
		assertThat(domain.certificateNo()).isEqualTo(entity.getCertificateNo());
		assertThat(domain.issuingBody()).isEqualTo(entity.getIssuingBody());
		assertThat(domain.validFrom()).isEqualTo(entity.getValidFrom());
		assertThat(domain.expiryDate()).isEqualTo(entity.getExpiryDate());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.renewsCredentialId()).isEqualTo(entity.getRenewsCredentialId());
		assertThat(domain.reviewNote()).isEqualTo(entity.getReviewNote());
		assertThat(domain.reviewedAt()).isEqualTo(entity.getReviewedAt());

		CredentialJpaEntity back = CredentialMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getCredentialTypeId()).isEqualTo(entity.getCredentialTypeId());
		assertThat(back.getReviewedByUserId()).isEqualTo(entity.getReviewedByUserId());
		assertThat(back.getCertificateNo()).isEqualTo(entity.getCertificateNo());
		assertThat(back.getIssuingBody()).isEqualTo(entity.getIssuingBody());
		assertThat(back.getValidFrom()).isEqualTo(entity.getValidFrom());
		assertThat(back.getExpiryDate()).isEqualTo(entity.getExpiryDate());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getRenewsCredentialId()).isEqualTo(entity.getRenewsCredentialId());
		assertThat(back.getReviewNote()).isEqualTo(entity.getReviewNote());
		assertThat(back.getReviewedAt()).isEqualTo(entity.getReviewedAt());
	}
}
