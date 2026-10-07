package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderFamilyBindingJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class ElderFamilyBindingMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		ElderFamilyBindingJpaEntity entity = new ElderFamilyBindingJpaEntity();
		entity.setId(1L);
		entity.setElderId(2L);
		entity.setFamilyMemberId(3L);
		entity.setRelationship(ElderFamilyBindingJpaEntity.Relationship.SON);
		entity.setIsPrimaryContact(true);
		entity.setAccessScope(ElderFamilyBindingJpaEntity.AccessScope.FULL);
		entity.setStatus(ElderFamilyBindingJpaEntity.Status.PENDING_CONFIRMATION);
		entity.setConfirmedAt(LocalDateTime.of(2026, 9, 6, 10, 8));
		entity.setExpiresAt(LocalDateTime.of(2026, 9, 6, 10, 9));

		ElderFamilyBinding domain = ElderFamilyBindingMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.familyMemberId()).isEqualTo(entity.getFamilyMemberId());
		assertThat(domain.relationship().name()).isEqualTo(entity.getRelationship().name());
		assertThat(domain.isPrimaryContact()).isEqualTo(entity.isIsPrimaryContact());
		assertThat(domain.accessScope().name()).isEqualTo(entity.getAccessScope().name());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.confirmedAt()).isEqualTo(entity.getConfirmedAt());
		assertThat(domain.expiresAt()).isEqualTo(entity.getExpiresAt());

		ElderFamilyBindingJpaEntity back = ElderFamilyBindingMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getFamilyMemberId()).isEqualTo(entity.getFamilyMemberId());
		assertThat(back.getRelationship()).isEqualTo(entity.getRelationship());
		assertThat(back.isIsPrimaryContact()).isEqualTo(entity.isIsPrimaryContact());
		assertThat(back.getAccessScope()).isEqualTo(entity.getAccessScope());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getConfirmedAt()).isEqualTo(entity.getConfirmedAt());
		assertThat(back.getExpiresAt()).isEqualTo(entity.getExpiresAt());
	}
}
