package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosterChangeJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosterChangeJpaRepository;

/** Implements the roster_change port with Spring Data; maps at the boundary and nothing else. */
@Repository
class RosterChangeRepositoryAdapter implements RosterChangeRepository {

	private final RosterChangeJpaRepository jpa;

	RosterChangeRepositoryAdapter(RosterChangeJpaRepository jpa) {
		this.jpa = jpa;
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
	public RosterChange save(RosterChange change) {
		return RosterChangeMapper.toDomain(jpa.save(RosterChangeMapper.toEntity(change)));
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
