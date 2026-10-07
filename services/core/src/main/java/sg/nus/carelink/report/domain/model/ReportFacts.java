package sg.nus.carelink.report.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one elder's period produced, before anybody's view of it: the report's working
 * draft ("底稿", UC-MG07 step 2).
 *
 * <p>One set of facts becomes three reports. That is the premise of the whole use case -
 * "同一份底稿按读者过滤出三个版本" - so this record holds no opinion about who may read what.
 * Caregiver names, raw readings and the caregivers' own words are all here; deciding how
 * much of each a family, a regulator or the institution sees is the job of the assembler
 * for that audience.
 *
 * <p>Plain data gathered through {@code ReportFactsSource}. No JPA, no Jackson, nothing that
 * ties it to where it was read from, so the assemblers can be tested by building one by hand.
 *
 * @param visits       every visit scheduled in the period, whatever became of it, oldest first
 * @param vitals       every reading taken on those visits, oldest first
 * @param observations the caregivers' notes on those visits
 * @param incidents    every incident reported in the period, with its timeline
 */
public record ReportFacts(
		Long elderId,
		ReportPeriod period,
		List<VisitFact> visits,
		List<VitalFact> vitals,
		List<ObservationFact> observations,
		List<IncidentFact> incidents) {

	public ReportFacts {
		Objects.requireNonNull(elderId, "elderId");
		Objects.requireNonNull(period, "period");
		visits = visits == null ? List.of() : List.copyOf(visits);
		vitals = vitals == null ? List.of() : List.copyOf(vitals);
		observations = observations == null ? List.of() : List.copyOf(observations);
		incidents = incidents == null ? List.of() : List.copyOf(incidents);
	}

	/**
	 * The visits whose record is not final yet (UC-MG07 exception 2a).
	 *
	 * <p>Derived from {@link #visits} rather than handed in beside them, so the two can never
	 * disagree: a report that lists a gap its own service section does not show, or the other
	 * way round, is the "内容残缺却标记为完成" the use case forbids.
	 */
	public List<VisitFact> unclosedVisits() {
		return visits.stream().filter(visit -> !visit.isClosed()).toList();
	}

	/** The visit an observation or a reading belongs to, if it is one of this period's. */
	public Optional<VisitFact> visit(Long visitId) {
		return visits.stream().filter(visit -> visit.id().equals(visitId)).findFirst();
	}
}
