package sg.nus.carelink.report.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sg.nus.carelink.report.domain.model.ReportBasis;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.repository.ReportBasisRepository;

/**
 * Test double for the basis port: keeps each basis with the facts it was stored with, so a test
 * can see what a run read and that it read it once.
 */
class InMemoryReportBasisRepository implements ReportBasisRepository {

	private final Map<Long, ReportBasis> rows = new LinkedHashMap<>();
	private final Map<Long, ReportFacts> facts = new LinkedHashMap<>();
	private long nextId = 1;

	@Override
	public ReportBasis save(ReportBasis basis, ReportFacts from) {
		if (basis.id() != null) {
			throw new IllegalArgumentException("a basis is stored once");
		}
		ReportBasis stored = new ReportBasis(nextId++, basis.elderId(), basis.period(), basis.metrics(), basis.createdAt());
		rows.put(stored.id(), stored);
		facts.put(stored.id(), from);
		return stored;
	}

	@Override
	public Map<Long, ReportMetrics> findMetrics(Collection<Long> basisIds) {
		return basisIds.stream().filter(rows::containsKey)
				.collect(Collectors.toMap(id -> id, id -> rows.get(id).metrics()));
	}

	List<ReportBasis> stored() {
		return List.copyOf(rows.values());
	}

	ReportFacts factsOf(Long basisId) {
		return facts.get(basisId);
	}
}
