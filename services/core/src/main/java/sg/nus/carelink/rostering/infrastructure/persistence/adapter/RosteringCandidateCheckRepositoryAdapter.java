package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateCheckRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringCandidateCheckJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class RosteringCandidateCheckRepositoryAdapter implements RosteringCandidateCheckRepository {

	private final RosteringCandidateCheckJpaRepository jpa;

	RosteringCandidateCheckRepositoryAdapter(RosteringCandidateCheckJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<RosteringCandidateCheck> findById(Long id) {
		return jpa.findById(id).map(RosteringCandidateCheckMapper::toDomain);
	}

	@Override
	public RosteringCandidateCheck save(RosteringCandidateCheck rosteringCandidateCheck) {
		return RosteringCandidateCheckMapper.toDomain(jpa.save(RosteringCandidateCheckMapper.toEntity(rosteringCandidateCheck)));
	}

	@Override
	public List<RosteringCandidateCheck> findByCandidateIds(Collection<Long> rosteringCandidateIds) {
		if (rosteringCandidateIds.isEmpty()) {
			return List.of();
		}
		return jpa.findByRosteringCandidateIdInOrderByIdAsc(rosteringCandidateIds).stream()
				.map(RosteringCandidateCheckMapper::toDomain)
				.toList();
	}
}
