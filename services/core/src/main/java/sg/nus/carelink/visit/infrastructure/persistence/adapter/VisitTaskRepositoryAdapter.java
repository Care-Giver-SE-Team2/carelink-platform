package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.visit.domain.model.VisitTask;
import sg.nus.carelink.visit.domain.repository.VisitTaskRepository;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitTaskJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class VisitTaskRepositoryAdapter implements VisitTaskRepository {

	private final VisitTaskJpaRepository jpa;

	VisitTaskRepositoryAdapter(VisitTaskJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public java.util.List<VisitTask> findByVisitId(Long visitId) {
		return jpa.findByVisitIdOrderByIdAsc(visitId).stream().map(VisitTaskMapper::toDomain).toList();
	}

	@Override
	public Optional<VisitTask> findById(Long id) {
		return jpa.findById(id).map(VisitTaskMapper::toDomain);
	}

	@Override
	public VisitTask save(VisitTask visitTask) {
		return VisitTaskMapper.toDomain(jpa.save(VisitTaskMapper.toEntity(visitTask)));
	}
}
