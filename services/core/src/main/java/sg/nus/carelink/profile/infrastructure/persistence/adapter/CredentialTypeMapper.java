package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialTypeJpaEntity;

/**
 * JPA entity <-> domain model for credential_type, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CredentialTypeMapperTest.
 */
final class CredentialTypeMapper {

	private CredentialTypeMapper() {
	}

	static CredentialType toDomain(CredentialTypeJpaEntity e) {
		return new CredentialType(
				e.getId(),
				e.getName(),
				e.getCreatedAt());
	}

	static CredentialTypeJpaEntity toEntity(CredentialType d) {
		CredentialTypeJpaEntity e = new CredentialTypeJpaEntity();
		e.setId(d.id());
		e.setName(d.name());
		return e;
	}
}
