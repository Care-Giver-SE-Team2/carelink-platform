package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class VisitMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		VisitJpaEntity entity = new VisitJpaEntity();
		entity.setId(1L);
		entity.setElderId(2L);
		entity.setCaregiverId(3L);
		entity.setCarePlanNodeId(4L);
		entity.setAbsenceId(5L);
		entity.setServiceType("v6");
		entity.setScheduledStart(LocalDateTime.of(2026, 9, 6, 10, 7));
		entity.setScheduledEnd(LocalDateTime.of(2026, 9, 6, 10, 8));
		entity.setCheckedInAt(LocalDateTime.of(2026, 9, 6, 10, 9));
		entity.setCheckedOutAt(LocalDateTime.of(2026, 9, 6, 10, 10));
		entity.setStatus(VisitJpaEntity.Status.SCHEDULED);
		entity.setStateDeadline(LocalDateTime.of(2026, 9, 6, 10, 12));
		entity.setCarePlanId(13L);
		entity.setVersion(14);

		Visit domain = VisitMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.carePlanNodeId()).isEqualTo(entity.getCarePlanNodeId());
		assertThat(domain.absenceId()).isEqualTo(entity.getAbsenceId());
		assertThat(domain.serviceType()).isEqualTo(entity.getServiceType());
		assertThat(domain.scheduledStart()).isEqualTo(entity.getScheduledStart());
		assertThat(domain.scheduledEnd()).isEqualTo(entity.getScheduledEnd());
		assertThat(domain.checkedInAt()).isEqualTo(entity.getCheckedInAt());
		assertThat(domain.checkedOutAt()).isEqualTo(entity.getCheckedOutAt());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.stateDeadline()).isEqualTo(entity.getStateDeadline());
		assertThat(domain.carePlanId()).isEqualTo(entity.getCarePlanId());
		assertThat(domain.version()).isEqualTo(entity.getVersion());

		VisitJpaEntity back = VisitMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getCarePlanNodeId()).isEqualTo(entity.getCarePlanNodeId());
		assertThat(back.getAbsenceId()).isEqualTo(entity.getAbsenceId());
		assertThat(back.getServiceType()).isEqualTo(entity.getServiceType());
		assertThat(back.getScheduledStart()).isEqualTo(entity.getScheduledStart());
		assertThat(back.getScheduledEnd()).isEqualTo(entity.getScheduledEnd());
		assertThat(back.getCheckedInAt()).isEqualTo(entity.getCheckedInAt());
		assertThat(back.getCheckedOutAt()).isEqualTo(entity.getCheckedOutAt());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getStateDeadline()).isEqualTo(entity.getStateDeadline());
		assertThat(back.getCarePlanId()).isEqualTo(entity.getCarePlanId());
		assertThat(back.getVersion()).isEqualTo(entity.getVersion());
	}
}
