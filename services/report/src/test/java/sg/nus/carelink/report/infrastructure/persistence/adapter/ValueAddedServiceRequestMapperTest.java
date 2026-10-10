package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceRequestJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class ValueAddedServiceRequestMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		ValueAddedServiceRequestJpaEntity entity = new ValueAddedServiceRequestJpaEntity();
		entity.setId(1L);
		entity.setElderId(2L);
		entity.setValueAddedServiceId(3L);
		entity.setRequestedByFamilyMemberId(4L);
		entity.setApprovingFamilyMemberId(5L);
		entity.setVisitId(6L);
		entity.setRequestedSchedule(LocalDateTime.of(2026, 9, 6, 10, 7));
		entity.setSpecialInstructions("v8");
		entity.setStatus(ValueAddedServiceRequestJpaEntity.Status.PENDING_APPROVAL);
		entity.setDecidedAt(LocalDateTime.of(2026, 9, 6, 10, 10));

		ValueAddedServiceRequest domain = ValueAddedServiceRequestMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.valueAddedServiceId()).isEqualTo(entity.getValueAddedServiceId());
		assertThat(domain.requestedByFamilyMemberId()).isEqualTo(entity.getRequestedByFamilyMemberId());
		assertThat(domain.approvingFamilyMemberId()).isEqualTo(entity.getApprovingFamilyMemberId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.requestedSchedule()).isEqualTo(entity.getRequestedSchedule());
		assertThat(domain.specialInstructions()).isEqualTo(entity.getSpecialInstructions());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.decidedAt()).isEqualTo(entity.getDecidedAt());

		ValueAddedServiceRequestJpaEntity back = ValueAddedServiceRequestMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getValueAddedServiceId()).isEqualTo(entity.getValueAddedServiceId());
		assertThat(back.getRequestedByFamilyMemberId()).isEqualTo(entity.getRequestedByFamilyMemberId());
		assertThat(back.getApprovingFamilyMemberId()).isEqualTo(entity.getApprovingFamilyMemberId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getRequestedSchedule()).isEqualTo(entity.getRequestedSchedule());
		assertThat(back.getSpecialInstructions()).isEqualTo(entity.getSpecialInstructions());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getDecidedAt()).isEqualTo(entity.getDecidedAt());
	}
}
