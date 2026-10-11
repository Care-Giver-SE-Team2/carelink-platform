package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.visit.domain.model.VisitEvidence;
import sg.nus.carelink.visit.domain.repository.VisitEvidenceRepository;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitEvidenceJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class VisitEvidenceRepositoryAdapter implements VisitEvidenceRepository {

	private final VisitEvidenceJpaRepository jpa;

	VisitEvidenceRepositoryAdapter(VisitEvidenceJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<VisitEvidence> findById(Long id) {
		return jpa.findById(id).map(VisitEvidenceMapper::toDomain);
	}

	@Override
	public VisitEvidence save(VisitEvidence visitEvidence) {
		return VisitEvidenceMapper.toDomain(jpa.save(VisitEvidenceMapper.toEntity(visitEvidence)));
	}
}
