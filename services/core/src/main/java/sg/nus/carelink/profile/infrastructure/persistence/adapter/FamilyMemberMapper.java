package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.infrastructure.persistence.entity.FamilyMemberJpaEntity;

/**
 * JPA entity <-> domain model for family_member, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by FamilyMemberMapperTest.
 */
final class FamilyMemberMapper {

	private FamilyMemberMapper() {
	}

	static FamilyMember toDomain(FamilyMemberJpaEntity e) {
		return new FamilyMember(
				e.getId(),
				e.getUserId(),
				e.getFullName(),
				e.getPhone(),
				e.getResidentialAddress(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static FamilyMemberJpaEntity toEntity(FamilyMember d) {
		FamilyMemberJpaEntity e = new FamilyMemberJpaEntity();
		e.setId(d.id());
		e.setUserId(d.userId());
		e.setFullName(d.fullName());
		e.setPhone(d.phone());
		e.setResidentialAddress(d.residentialAddress());
		return e;
	}
}
