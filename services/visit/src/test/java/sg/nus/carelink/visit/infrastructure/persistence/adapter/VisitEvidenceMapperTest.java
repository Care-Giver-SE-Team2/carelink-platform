package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitEvidence;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitEvidenceJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class VisitEvidenceMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		VisitEvidenceJpaEntity entity = new VisitEvidenceJpaEntity();
		entity.setId(1L);
		entity.setVisitId(2L);
		entity.setKind(VisitEvidenceJpaEntity.Kind.PHOTO);
		entity.setReference("v4");
		entity.setVerificationStatus(VisitEvidenceJpaEntity.VerificationStatus.UNVERIFIED);
		entity.setCapturedAt(LocalDateTime.of(2026, 9, 6, 10, 6));

		VisitEvidence domain = VisitEvidenceMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.kind().name()).isEqualTo(entity.getKind().name());
		assertThat(domain.reference()).isEqualTo(entity.getReference());
		assertThat(domain.verificationStatus().name()).isEqualTo(entity.getVerificationStatus().name());
		assertThat(domain.capturedAt()).isEqualTo(entity.getCapturedAt());

		VisitEvidenceJpaEntity back = VisitEvidenceMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getKind()).isEqualTo(entity.getKind());
		assertThat(back.getReference()).isEqualTo(entity.getReference());
		assertThat(back.getVerificationStatus()).isEqualTo(entity.getVerificationStatus());
		assertThat(back.getCapturedAt()).isEqualTo(entity.getCapturedAt());
	}
}
