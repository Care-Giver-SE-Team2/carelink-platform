package sg.nus.carelink.report.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;

/**
 * Test double for the facts port: hands back facts written by hand, whatever period is asked
 * for, and remembers what it was asked so a test can see whether the tables would have been
 * read at all.
 */
class FakeReportFactsSource implements ReportFactsSource {

	private final Map<Long, ReportFacts> byElder = new LinkedHashMap<>();
	private final List<Long> gatheredFor = new ArrayList<>();
	private ReportPeriod lastPeriodAsked;

	FakeReportFactsSource with(ReportFacts facts) {
		byElder.put(facts.elderId(), facts);
		return this;
	}

	@Override
	public Optional<ReportFacts> gather(Long elderId, ReportPeriod period) {
		gatheredFor.add(elderId);
		lastPeriodAsked = period;
		return Optional.ofNullable(byElder.get(elderId));
	}

	/** Every elder it holds facts for counts as having had a visit, whatever the period. */
	@Override
	public List<Long> eldersWithVisitsIn(ReportPeriod period) {
		lastPeriodAsked = period;
		return List.copyOf(byElder.keySet());
	}

	List<Long> gatheredFor() {
		return List.copyOf(gatheredFor);
	}

	ReportPeriod lastPeriodAsked() {
		return lastPeriodAsked;
	}
}
