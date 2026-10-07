package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlanRequiredCredential;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanRequiredCredentialJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CarePlanRequiredCredentialMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CarePlanRequiredCredentialJpaEntity entity = new CarePlanRequiredCredentialJpaEntity();
		entity.setId(new CarePlanRequiredCredentialJpaEntity.Id(1L, 2L));

		CarePlanRequiredCredential domain = CarePlanRequiredCredentialMapper.toDomain(entity);
		assertThat(domain.id().carePlanId()).isEqualTo(1L);
		assertThat(domain.id().credentialTypeId()).isEqualTo(2L);

		CarePlanRequiredCredentialJpaEntity back = CarePlanRequiredCredentialMapper.toEntity(domain);
		assertThat(back.getId().getCarePlanId()).isEqualTo(1L);
		assertThat(back.getId().getCredentialTypeId()).isEqualTo(2L);
	}
}
