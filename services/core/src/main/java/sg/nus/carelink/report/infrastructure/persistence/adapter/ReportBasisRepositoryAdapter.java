package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.report.domain.model.ReportBasis;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.repository.ReportBasisRepository;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportBasisJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ReportBasisJpaRepository;

/**
 * Implements the basis port with Spring Data. Insert-only, as the report adapter is: a basis
 * that already has an id is refused rather than merged over the stored one.
 */
@Repository
class ReportBasisRepositoryAdapter implements ReportBasisRepository {

	private final ReportBasisJpaRepository bases;

	ReportBasisRepositoryAdapter(ReportBasisJpaRepository bases) {
		this.bases = bases;
	}

	@Override
	public ReportBasis save(ReportBasis basis, ReportFacts facts) {
		if (basis.id() != null) {
			throw new IllegalArgumentException("Basis %d is already on file; a basis is stored once".formatted(basis.id()));
		}
		if (!Objects.equals(basis.elderId(), facts.elderId()) || !basis.period().equals(facts.period())) {
			throw new IllegalArgumentException("A basis is stored with the facts it was made from");
		}
		return ReportBasisMapper.toDomain(bases.save(ReportBasisMapper.toEntity(basis, facts)));
	}

	@Override
	public Map<Long, ReportMetrics> findMetrics(Collection<Long> basisIds) {
		Map<Long, ReportMetrics> metrics = new HashMap<>();
		for (ReportBasisJpaEntity row : bases.findAllById(basisIds)) {
			metrics.put(row.getId(), ReportBasisMapper.metrics(row));
		}
		return metrics;
	}
}
