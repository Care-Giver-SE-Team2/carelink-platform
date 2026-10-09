package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.domain.repository.RosteringConstraintRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringConstraintJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class RosteringConstraintRepositoryAdapter implements RosteringConstraintRepository {

	private final RosteringConstraintJpaRepository jpa;

	RosteringConstraintRepositoryAdapter(RosteringConstraintJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<RosteringConstraint> findById(Long id) {
		return jpa.findById(id).map(RosteringConstraintMapper::toDomain);
	}

	@Override
	public RosteringConstraint save(RosteringConstraint rosteringConstraint) {
		return RosteringConstraintMapper.toDomain(jpa.save(RosteringConstraintMapper.toEntity(rosteringConstraint)));
	}

	@Override
	public List<RosteringConstraint> findAll() {
		return jpa.findAll(Sort.by("id")).stream().map(RosteringConstraintMapper::toDomain).toList();
	}
}
