package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderFamilyBindingJpaEntity;

/**
 * JPA entity <-> domain model for elder_family_binding, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by ElderFamilyBindingMapperTest.
 */
final class ElderFamilyBindingMapper {

	private ElderFamilyBindingMapper() {
	}

	static ElderFamilyBinding toDomain(ElderFamilyBindingJpaEntity e) {
		return new ElderFamilyBinding(
				e.getId(),
				e.getElderId(),
				e.getFamilyMemberId(),
				e.getRelationship() == null ? null : ElderFamilyBinding.Relationship.valueOf(e.getRelationship().name()),
				e.isIsPrimaryContact(),
				e.getAccessScope() == null ? null : ElderFamilyBinding.AccessScope.valueOf(e.getAccessScope().name()),
				e.getStatus() == null ? null : ElderFamilyBinding.Status.valueOf(e.getStatus().name()),
				e.getConfirmedAt(),
				e.getExpiresAt(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static ElderFamilyBindingJpaEntity toEntity(ElderFamilyBinding d) {
		ElderFamilyBindingJpaEntity e = new ElderFamilyBindingJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setFamilyMemberId(d.familyMemberId());
		e.setRelationship(d.relationship() == null ? null : ElderFamilyBindingJpaEntity.Relationship.valueOf(d.relationship().name()));
		e.setIsPrimaryContact(d.isPrimaryContact());
		e.setAccessScope(d.accessScope() == null ? null : ElderFamilyBindingJpaEntity.AccessScope.valueOf(d.accessScope().name()));
		e.setStatus(d.status() == null ? null : ElderFamilyBindingJpaEntity.Status.valueOf(d.status().name()));
		e.setConfirmedAt(d.confirmedAt());
		e.setExpiresAt(d.expiresAt());
		return e;
	}
}
