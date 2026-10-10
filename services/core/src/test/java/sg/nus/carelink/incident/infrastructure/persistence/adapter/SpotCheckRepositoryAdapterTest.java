package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.SpotCheckUpdated;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.infrastructure.persistence.entity.SpotCheckJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.SpotCheckJpaRepository;

/**
 * The adapter delegates to Spring Data and maps at the boundary, and publishes every save as
 * {@code SpotCheckUpdated}.
 */
class SpotCheckRepositoryAdapterTest {

	private final SpotCheckJpaRepository jpa = mock(SpotCheckJpaRepository.class);
	private final Events events = mock(Events.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneId.of("Asia/Singapore"));
	private final SpotCheckRepositoryAdapter adapter = new SpotCheckRepositoryAdapter(jpa, events, clock);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		SpotCheckJpaEntity entity = new SpotCheckJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<SpotCheck> found = adapter.findById(7L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(7L);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		SpotCheckJpaEntity entity = new SpotCheckJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(SpotCheckJpaEntity.class))).thenReturn(entity);

		SpotCheck saved = adapter.save(SpotCheckMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}

	@Test
	void everySaveIsPublishedWithTheSpotChecksWholeState() {
		SpotCheckJpaEntity entity = new SpotCheckJpaEntity();
		entity.setId(41L);
		entity.setElderId(101L);
		entity.setVisitId(812L);
		entity.setCaregiverId(9L);
		entity.setProposedTime(LocalDateTime.of(2026, 10, 14, 10, 0));
		entity.setApprovalStatus(SpotCheckJpaEntity.ApprovalStatus.APPROVED);
		when(jpa.save(any(SpotCheckJpaEntity.class))).thenReturn(entity);

		adapter.save(SpotCheckMapper.toDomain(entity));

		verify(events).publish(SpotCheckUpdated.TYPE, new SpotCheckUpdated(41L, 101L, 812L, 9L,
				OffsetDateTime.parse("2026-10-14T10:00:00+08:00"), "APPROVED", null, null, null, null, null,
				OffsetDateTime.parse("2026-10-12T20:00:00+08:00")));
	}

	@Test
	void findersPickTheirQueryAndAnEmptyElderListAsksNothing() {
		SpotCheckJpaEntity row = new SpotCheckJpaEntity();
		row.setId(7L);
		row.setApprovalStatus(SpotCheckJpaEntity.ApprovalStatus.APPROVED);
		LocalDateTime since = LocalDateTime.of(2026, 7, 1, 0, 0);
		when(jpa.findAllByOrderByProposedTimeDescIdDesc()).thenReturn(List.of(row));
		when(jpa.findByElderIdInOrderByProposedTimeDescIdDesc(Set.of(2L))).thenReturn(List.of(row));
		when(jpa.findByCaregiverIdOrderByProposedTimeDescIdDesc(3L)).thenReturn(List.of(row, row));
		when(jpa.findByOutcomeAndCheckedAtGreaterThanEqual(SpotCheckJpaEntity.Outcome.COMPLETED, since))
				.thenReturn(List.of(row));
		when(jpa.findByApprovalStatusAndOutcomeIsNull(SpotCheckJpaEntity.ApprovalStatus.PENDING_APPROVAL))
				.thenReturn(List.of(row));

		assertThat(adapter.findAll()).hasSize(1);
		assertThat(adapter.findByElderIds(Set.of(2L))).hasSize(1);
		assertThat(adapter.findByElderIds(Set.of())).isEmpty();
		assertThat(adapter.findByCaregiverId(3L)).hasSize(2);
		assertThat(adapter.findConcludedSince(since)).extracting(SpotCheck::id).containsExactly(7L);
		assertThat(adapter.findAwaitingConsent()).extracting(SpotCheck::id).containsExactly(7L);
	}
}
