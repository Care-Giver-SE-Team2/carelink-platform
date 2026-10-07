package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import sg.nus.carelink.careplan.domain.model.CarePlanRequiredCredential;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanRequiredCredentialJpaEntity;

/**
 * JPA entity <-> domain model for care_plan_required_credential, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CarePlanRequiredCredentialMapperTest.
 */
final class CarePlanRequiredCredentialMapper {

	private CarePlanRequiredCredentialMapper() {
	}

	static CarePlanRequiredCredential toDomain(CarePlanRequiredCredentialJpaEntity e) {
		return new CarePlanRequiredCredential(
				new CarePlanRequiredCredential.Id(e.getId().getCarePlanId(), e.getId().getCredentialTypeId()));
	}

	static CarePlanRequiredCredentialJpaEntity toEntity(CarePlanRequiredCredential d) {
		CarePlanRequiredCredentialJpaEntity e = new CarePlanRequiredCredentialJpaEntity();
		e.setId(new CarePlanRequiredCredentialJpaEntity.Id(d.id().carePlanId(), d.id().credentialTypeId()));
		return e;
	}
}
