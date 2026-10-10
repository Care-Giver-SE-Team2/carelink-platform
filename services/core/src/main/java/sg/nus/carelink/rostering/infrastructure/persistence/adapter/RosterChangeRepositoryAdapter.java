package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.RosterChangeUpdated;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosterChangeJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosterChangeJpaRepository;

/**
 * Implements the roster_change port with Spring Data; maps at the boundary. Every save is
 * published as {@code RosterChangeUpdated}, carrying the change's whole state, in the transaction
 * that saves it.
 */
@Repository
class RosterChangeRepositoryAdapter implements RosterChangeRepository {

	private final RosterChangeJpaRepository jpa;

	private final Events events;

	private final Clock clock;

	RosterChangeRepositoryAdapter(RosterChangeJpaRepository jpa, Events events, Clock clock) {
		this.jpa = jpa;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public Optional<RosterChange> findById(Long id) {
		return jpa.findById(id).map(RosterChangeMapper::toDomain);
	}

	@Override
	public Optional<RosterChange> lock(Long id) {
		return jpa.lockById(id).map(RosterChangeMapper::toDomain);
	}

	@Override
	@Transactional
	public RosterChange save(RosterChange change) {
		RosterChange saved = RosterChangeMapper.toDomain(jpa.save(RosterChangeMapper.toEntity(change)));
		events.publish(RosterChangeUpdated.TYPE, RosterChangeEventMapper.updated(saved, LocalDateTime.now(clock)));
		return saved;
	}

	@Override
	public List<RosterChange> findByAbsenceId(Long absenceId) {
		return jpa.findByAbsenceIdOrderByVisitStartAscIdAsc(absenceId).stream().map(RosterChangeMapper::toDomain).toList();
	}

	@Override
	public List<RosterChange> findByElderIds(Collection<Long> elderIds) {
		if (elderIds.isEmpty()) {
			return List.of();
		}
		return jpa.findByElderIdInOrderByVisitStartAscIdAsc(elderIds).stream().map(RosterChangeMapper::toDomain).toList();
	}

	@Override
	public List<RosterChange> findAwaitingFamilyDueBy(LocalDateTime now) {
		return jpa.findByStatusAndRespondByLessThanEqualOrderByRespondByAsc(RosterChangeJpaEntity.Status.AWAITING_FAMILY, now)
				.stream().map(RosterChangeMapper::toDomain).toList();
	}
}
