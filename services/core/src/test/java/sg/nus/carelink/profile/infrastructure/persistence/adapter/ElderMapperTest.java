package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class ElderMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		ElderJpaEntity entity = new ElderJpaEntity();
		entity.setId(1L);
		entity.setUserId(2L);
		entity.setFullName("v3");
		entity.setGender(ElderJpaEntity.Gender.MALE);
		entity.setDateOfBirth(LocalDate.of(2026, 9, 6));
		entity.setPhone("v6");
		entity.setAddress("v7");
		entity.setPostalCode("v8");
		entity.setSector("v9");
		entity.setPreferredDialects("v10");
		entity.setLivesAlone(Boolean.TRUE);
		entity.setMobilityLevel(ElderJpaEntity.MobilityLevel.INDEPENDENT);
		entity.setContinuityPreference(ElderJpaEntity.ContinuityPreference.PREFERRED);
		entity.setMedicalNotes("v14");

		Elder domain = ElderMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.userId()).isEqualTo(entity.getUserId());
		assertThat(domain.fullName()).isEqualTo(entity.getFullName());
		assertThat(domain.gender().name()).isEqualTo(entity.getGender().name());
		assertThat(domain.dateOfBirth()).isEqualTo(entity.getDateOfBirth());
		assertThat(domain.phone()).isEqualTo(entity.getPhone());
		assertThat(domain.address()).isEqualTo(entity.getAddress());
		assertThat(domain.postalCode()).isEqualTo(entity.getPostalCode());
		assertThat(domain.sector()).isEqualTo(entity.getSector());
		assertThat(domain.preferredDialects()).isEqualTo(entity.getPreferredDialects());
		assertThat(domain.livesAlone()).isEqualTo(entity.getLivesAlone());
		assertThat(domain.mobilityLevel().name()).isEqualTo(entity.getMobilityLevel().name());
		assertThat(domain.continuityPreference().name()).isEqualTo(entity.getContinuityPreference().name());
		assertThat(domain.medicalNotes()).isEqualTo(entity.getMedicalNotes());

		ElderJpaEntity back = ElderMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getUserId()).isEqualTo(entity.getUserId());
		assertThat(back.getFullName()).isEqualTo(entity.getFullName());
		assertThat(back.getGender()).isEqualTo(entity.getGender());
		assertThat(back.getDateOfBirth()).isEqualTo(entity.getDateOfBirth());
		assertThat(back.getPhone()).isEqualTo(entity.getPhone());
		assertThat(back.getAddress()).isEqualTo(entity.getAddress());
		assertThat(back.getPostalCode()).isEqualTo(entity.getPostalCode());
		assertThat(back.getSector()).isEqualTo(entity.getSector());
		assertThat(back.getPreferredDialects()).isEqualTo(entity.getPreferredDialects());
		assertThat(back.getLivesAlone()).isEqualTo(entity.getLivesAlone());
		assertThat(back.getMobilityLevel()).isEqualTo(entity.getMobilityLevel());
		assertThat(back.getContinuityPreference()).isEqualTo(entity.getContinuityPreference());
		assertThat(back.getMedicalNotes()).isEqualTo(entity.getMedicalNotes());
	}
}
