package sg.nus.carelink.report.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.ReviewFact;
import sg.nus.carelink.report.domain.model.RosterChangeFact;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.domain.model.ValueAddedFact;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;

/**
 * One elder's week, written out by hand: the facts every report test starts from.
 *
 * <p>Built to exercise every rule the three readers differ on in a single set of facts - an
 * elder with a name, an age and medical notes, a caregiver with a name and a number, a reading
 * that was out of range, notes in the caregiver's own words, an incident with staff names on
 * its timeline, the elder's comments, a family review, a spot-check finding, an absence still
 * waiting for the family, a value-added request, and one visit that is not closed. The
 * assembler tests feed this same instance to all three readers, so the audience is the only
 * thing that changes between them.
 */
public final class ReportFixtures {

	public static final Long ELDER = 1L;
	public static final ReportPeriod WEEK = new ReportPeriod(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20));
	public static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 20, 23, 0);

	public static final VisitFact MONDAY = new VisitFact(
			11L, 3L, "Daniel Goh", "Personal care", LocalDateTime.of(2026, 9, 14, 9, 0),
			VisitFact.Status.VERIFIED, 2, 2, false, 60, 55);
	public static final VisitFact WEDNESDAY = new VisitFact(
			12L, 3L, "Daniel Goh", "Personal care", LocalDateTime.of(2026, 9, 16, 9, 0),
			VisitFact.Status.VERIFIED, 2, 2, false, 60, 65);
	/** Not closed: the week's report has to say so and name it. */
	public static final VisitFact FRIDAY = new VisitFact(
			13L, 3L, "Daniel Goh", "Personal care", LocalDateTime.of(2026, 9, 18, 9, 0),
			VisitFact.Status.SCHEDULED, 0, 0, false, 60, null);

	public static final String MEDICAL_NOTES = "Hypertension, on amlodipine.";
	/** Born 2 March 1941: 85 at the end of the week, in the band 80–89. */
	public static final ElderProfile TAN_AH_MEI = new ElderProfile(
			"Tan Ah Mei", "FEMALE", LocalDate.of(1941, 3, 2), "ASSISTIVE_CANE", true, MEDICAL_NOTES,
			3L, "Daniel Goh", 3, new BigDecimal("6.50"));

	public static final String ELDER_COMMENT = "He left before the exercises were done.";
	public static final String REVIEW_NOTES = "Kind, but sometimes rushed.";
	public static final String FINDING = "Gloves were not changed between tasks.";
	public static final String CAREGIVER_RESPONSE = "I will carry a spare pair.";

	public static final String MOBILITY_NOTE = "Walked to the void deck with the cane, steady on her feet.";
	public static final String MEALS_NOTE = "Ate half of lunch; said she was not hungry.";

	public static final String FALL_DESCRIPTION = "Slipped getting out of the shower, no injury.";
	public static final String RESOLUTION = "HANDLED_ON_SITE :: No injury. Bathroom grab bar to be fitted this week.";

	private ReportFixtures() {
	}

	/** The week, with one visit still open. */
	public static ReportFacts week() {
		return new ReportFacts(
				ELDER,
				WEEK,
				List.of(MONDAY, WEDNESDAY, FRIDAY),
				List.of(
						reading(11L, "systolic", "128.00", "mmHg", false, 14),
						reading(11L, "diastolic", "82.00", "mmHg", false, 14),
						reading(11L, "pulse", "72.00", "bpm", false, 14),
						reading(11L, "temperature", "36.80", "°C", false, 14),
						reading(12L, "systolic", "142.00", "mmHg", true, 16),
						reading(12L, "diastolic", "88.00", "mmHg", false, 16),
						reading(12L, "pulse", "76.00", "bpm", false, 16),
						reading(12L, "temperature", "36.60", "°C", false, 16)),
				List.of(
						new ObservationFact(11L, "Mobility", MOBILITY_NOTE),
						new ObservationFact(12L, "Meals", MEALS_NOTE)),
				List.of(fall()),
				TAN_AH_MEI,
				new ReportFacts.Quality(
						List.of(
								new ConfirmationFact(11L, false, 5, "Very patient today.", LocalDateTime.of(2026, 9, 14, 11, 0)),
								new ConfirmationFact(12L, true, 2, ELDER_COMMENT, LocalDateTime.of(2026, 9, 16, 11, 0))),
						List.of(new ReviewFact(3L, "Daniel Goh", WEEK.start(), WEEK.end(), 4, 5, 4, REVIEW_NOTES,
								"RENEW_CURRENT")),
						List.of(new SpotCheckFact(4L, 3L, "Daniel Goh", LocalDateTime.of(2026, 9, 16, 10, 0), "APPROVED",
								"NEEDS_IMPROVEMENT", "COMPLETED", FINDING, CAREGIVER_RESPONSE,
								LocalDateTime.of(2026, 9, 16, 10, 40)))),
				new ReportFacts.Changes(
						List.of(new RosterChangeFact(13L, LocalDateTime.of(2026, 9, 18, 9, 0), 3L, "Daniel Goh",
								"AWAITING_FAMILY", null, null, null, null)),
						List.of(new ValueAddedFact(7L, "Hospital escort", LocalDateTime.of(2026, 9, 19, 10, 0),
								"DISPATCHED", 15L))));
	}

	/** The same week with nothing left open. */
	public static ReportFacts closedWeek() {
		ReportFacts week = week();
		return new ReportFacts(
				week.elderId(), week.period(), List.of(MONDAY, WEDNESDAY),
				week.vitals(), week.observations(), week.incidents(), week.elder(), week.quality(), week.changes());
	}

	/** A real elder with nothing recorded. */
	public static ReportFacts quietWeek() {
		return new ReportFacts(ELDER, WEEK, List.of(), List.of(), List.of(), List.of());
	}

	public static IncidentFact fall() {
		return new IncidentFact(
				2L, "FALL", "MEDIUM", "RESOLVED", FALL_DESCRIPTION,
				LocalDateTime.of(2026, 9, 15, 10, 15),
				LocalDateTime.of(2026, 9, 15, 11, 2),
				List.of(
						step("caregiver:20", "REPORTED", "reported by caregiver", 10, 15),
						step("system", "ASSIGNED", "responder=12 :: first responder", 10, 15),
						step("Ben Lim (demo-ben)", "CLAIMED", "taken over; countdown stopped", 10, 21),
						step("Ben Lim (demo-ben)", "RESOLVED", RESOLUTION, 11, 2)));
	}

	/** A small but valid content, for tests about storing and showing reports rather than writing them. */
	public static ReportContent content() {
		return new ReportContent(
				List.of(new ReportSection("Service completion", "3 visits: 1 scheduled, 2 verified.")),
				false,
				List.of("Visit 13 on 2026-09-18 not closed"),
				null,
				ReportContent.GeneratedBy.TEMPLATE);
	}

	/** A report as it comes back from storage. */
	public static Report stored(Long id, Report.Audience audience) {
		return new Report(id, ELDER, 7L, audience, WEEK, Report.Status.PUBLISHED, content(), List.of(), GENERATED_AT);
	}

	private static VitalFact reading(Long visit, String metric, String value, String unit, boolean outOfRange, int day) {
		return new VitalFact(visit, metric, new BigDecimal(value), unit, outOfRange, LocalDateTime.of(2026, 9, day, 9, 10));
	}

	private static IncidentFact.Step step(String actor, String action, String detail, int hour, int minute) {
		return new IncidentFact.Step(actor, action, detail, LocalDateTime.of(2026, 9, 15, hour, minute));
	}
}
