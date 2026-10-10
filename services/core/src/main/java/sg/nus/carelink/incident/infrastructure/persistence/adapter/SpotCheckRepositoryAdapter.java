package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.SpotCheckUpdated;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.domain.repository.SpotCheckRepository;
import sg.nus.carelink.incident.infrastructure.persistence.entity.SpotCheckJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.SpotCheckJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 *
 * <p>Every save is published as {@code SpotCheckUpdated}, carrying the spot check's whole state, in
 * the transaction that saves it.
 */
@Repository
class SpotCheckRepositoryAdapter implements SpotCheckRepository {

	private final SpotCheckJpaRepository jpa;

	private final Events events;

	private final Clock clock;

	SpotCheckRepositoryAdapter(SpotCheckJpaRepository jpa, Events events, Clock clock) {
		this.jpa = jpa;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public Optional<SpotCheck> findById(Long id) {
		return jpa.findById(id).map(SpotCheckMapper::toDomain);
	}

	@Override
	@Transactional
	public SpotCheck save(SpotCheck spotCheck) {
		SpotCheck saved = SpotCheckMapper.toDomain(jpa.save(SpotCheckMapper.toEntity(spotCheck)));
		events.publish(SpotCheckUpdated.TYPE, IncidentEventMapper.spotCheck(saved, LocalDateTime.now(clock)));
		return saved;
	}

	@Override
	public List<SpotCheck> findAll() {
		return jpa.findAllByOrderByProposedTimeDescIdDesc().stream().map(SpotCheckMapper::toDomain).toList();
	}

	@Override
	public List<SpotCheck> findByElderIds(Collection<Long> elderIds) {
		if (elderIds.isEmpty()) {
			return List.of();
		}
		return jpa.findByElderIdInOrderByProposedTimeDescIdDesc(elderIds).stream().map(SpotCheckMapper::toDomain).toList();
	}

	@Override
	public List<SpotCheck> findByCaregiverId(Long caregiverId) {
		return jpa.findByCaregiverIdOrderByProposedTimeDescIdDesc(caregiverId).stream()
				.map(SpotCheckMapper::toDomain)
				.toList();
	}

	@Override
	public List<SpotCheck> findConcludedSince(LocalDateTime since) {
		return jpa.findByOutcomeAndCheckedAtGreaterThanEqual(SpotCheckJpaEntity.Outcome.COMPLETED, since).stream()
				.map(SpotCheckMapper::toDomain)
				.toList();
	}

	@Override
	public List<SpotCheck> findAwaitingConsent() {
		return jpa.findByApprovalStatusAndOutcomeIsNull(SpotCheckJpaEntity.ApprovalStatus.PENDING_APPROVAL).stream()
				.map(SpotCheckMapper::toDomain)
				.toList();
	}
}
