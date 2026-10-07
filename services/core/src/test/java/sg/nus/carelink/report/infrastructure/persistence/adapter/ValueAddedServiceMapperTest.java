package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class ValueAddedServiceMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		ValueAddedServiceJpaEntity entity = new ValueAddedServiceJpaEntity();
		entity.setId(1L);
		entity.setName("v2");
		entity.setDescription("v3");
		entity.setStatus(ValueAddedServiceJpaEntity.Status.AVAILABLE);

		ValueAddedService domain = ValueAddedServiceMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.name()).isEqualTo(entity.getName());
		assertThat(domain.description()).isEqualTo(entity.getDescription());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());

		ValueAddedServiceJpaEntity back = ValueAddedServiceMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getName()).isEqualTo(entity.getName());
		assertThat(back.getDescription()).isEqualTo(entity.getDescription());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
	}
}
