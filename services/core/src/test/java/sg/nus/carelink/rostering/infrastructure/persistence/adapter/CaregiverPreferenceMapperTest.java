package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.CaregiverPreference;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverPreferenceJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CaregiverPreferenceMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CaregiverPreferenceJpaEntity entity = new CaregiverPreferenceJpaEntity();
		entity.setId(1L);
		entity.setCaregiverId(2L);
		entity.setPreferredServiceTypes("v3");
		entity.setPreferredSectors("v4");
		entity.setPreferredTimeWindows("v5");
		entity.setMaxVisitsPerDay(6);
		entity.setMaxHoursPerDay(new BigDecimal("7.5"));

		CaregiverPreference domain = CaregiverPreferenceMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.preferredServiceTypes()).isEqualTo(entity.getPreferredServiceTypes());
		assertThat(domain.preferredSectors()).isEqualTo(entity.getPreferredSectors());
		assertThat(domain.preferredTimeWindows()).isEqualTo(entity.getPreferredTimeWindows());
		assertThat(domain.maxVisitsPerDay()).isEqualTo(entity.getMaxVisitsPerDay());
		assertThat(domain.maxHoursPerDay()).isEqualTo(entity.getMaxHoursPerDay());

		CaregiverPreferenceJpaEntity back = CaregiverPreferenceMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getPreferredServiceTypes()).isEqualTo(entity.getPreferredServiceTypes());
		assertThat(back.getPreferredSectors()).isEqualTo(entity.getPreferredSectors());
		assertThat(back.getPreferredTimeWindows()).isEqualTo(entity.getPreferredTimeWindows());
		assertThat(back.getMaxVisitsPerDay()).isEqualTo(entity.getMaxVisitsPerDay());
		assertThat(back.getMaxHoursPerDay()).isEqualTo(entity.getMaxHoursPerDay());
	}
}
