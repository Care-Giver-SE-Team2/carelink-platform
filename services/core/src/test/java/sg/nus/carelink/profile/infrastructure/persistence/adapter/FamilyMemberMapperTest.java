package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.infrastructure.persistence.entity.FamilyMemberJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class FamilyMemberMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		FamilyMemberJpaEntity entity = new FamilyMemberJpaEntity();
		entity.setId(1L);
		entity.setUserId(2L);
		entity.setFullName("v3");
		entity.setPhone("v4");
		entity.setResidentialAddress("v5");

		FamilyMember domain = FamilyMemberMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.userId()).isEqualTo(entity.getUserId());
		assertThat(domain.fullName()).isEqualTo(entity.getFullName());
		assertThat(domain.phone()).isEqualTo(entity.getPhone());
		assertThat(domain.residentialAddress()).isEqualTo(entity.getResidentialAddress());

		FamilyMemberJpaEntity back = FamilyMemberMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getUserId()).isEqualTo(entity.getUserId());
		assertThat(back.getFullName()).isEqualTo(entity.getFullName());
		assertThat(back.getPhone()).isEqualTo(entity.getPhone());
		assertThat(back.getResidentialAddress()).isEqualTo(entity.getResidentialAddress());
	}
}
