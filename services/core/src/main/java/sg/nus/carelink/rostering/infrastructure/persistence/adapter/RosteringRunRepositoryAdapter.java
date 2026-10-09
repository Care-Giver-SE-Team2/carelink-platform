package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.repository.RosteringRunRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringRunJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class RosteringRunRepositoryAdapter implements RosteringRunRepository {

	private final RosteringRunJpaRepository jpa;

	RosteringRunRepositoryAdapter(RosteringRunJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<RosteringRun> findById(Long id) {
		return jpa.findById(id).map(RosteringRunMapper::toDomain);
	}

	@Override
	public RosteringRun save(RosteringRun rosteringRun) {
		return RosteringRunMapper.toDomain(jpa.save(RosteringRunMapper.toEntity(rosteringRun)));
	}
}
