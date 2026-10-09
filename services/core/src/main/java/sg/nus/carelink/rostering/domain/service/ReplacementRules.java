package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosteringConstraint;

/**
 * The rules of the replacement search, and how the rostering_constraint table switches them on
 * and sets their thresholds.
 *
 * <p>The order below is the order a candidate is excluded in: leave first, because "she is on
 * leave" is the answer a manager expects before "her certificate lapses that week".
 */
public final class ReplacementRules {

	public static final String NOT_ON_LEAVE = "NOT_ON_LEAVE";
	public static final String CERTIFICATION_VALID = "CERTIFICATION_VALID";
	public static final String NO_TIME_CLASH = "NO_TIME_CLASH";
	public static final String DAILY_VISIT_CAP = "DAILY_VISIT_CAP";
	public static final String DAILY_HOURS_CAP = "DAILY_HOURS_CAP";
	public static final String CONTINUITY = "CONTINUITY";
	public static final String SECTOR_BAND = "SECTOR_BAND";
	public static final String DIALECT_MATCH = "DIALECT_MATCH";
	public static final String SPOT_CHECK = "SPOT_CHECK";

	static final int DEFAULT_VISIT_CAP = 8;
	static final BigDecimal DEFAULT_HOURS_CAP = new BigDecimal("8.0");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private ReplacementRules() {
	}

	/**
	 * The enabled rules, in exclusion order, with the thresholds the table sets. A code the
	 * table has but nothing here implements (TRAVEL_DISTANCE needs coordinates CareLink does
	 * not keep) is ignored rather than failing the search.
	 */
	public static List<ReplacementRule> from(List<RosteringConstraint> constraints) {
		Map<String, RosteringConstraint> enabled = constraints.stream()
				.filter(RosteringConstraint::enabled)
				.collect(Collectors.toMap(RosteringConstraint::code, Function.identity(), (a, b) -> a));
		List<ReplacementRule> rules = new ArrayList<>();
		for (String code : List.of(NOT_ON_LEAVE, CERTIFICATION_VALID, NO_TIME_CLASH, DAILY_VISIT_CAP,
				DAILY_HOURS_CAP, CONTINUITY, SECTOR_BAND, DIALECT_MATCH, SPOT_CHECK)) {
			RosteringConstraint row = enabled.get(code);
			if (row != null) {
				rules.add(build(code, row.parameterValue()));
			}
		}
		return List.copyOf(rules);
	}

	/** Every rule with its default threshold: the search as V12 seeds it. */
	public static List<ReplacementRule> defaults() {
		return List.of(new NotOnLeave(), new CertificationValid(), new NoTimeClash(),
				new DailyVisitCap(DEFAULT_VISIT_CAP), new DailyHoursCap(DEFAULT_HOURS_CAP), new Continuity(),
				new SectorBand(), new DialectMatch(), new SpotCheck());
	}

	private static ReplacementRule build(String code, String parameter) {
		return switch (code) {
			case NOT_ON_LEAVE -> new NotOnLeave();
			case CERTIFICATION_VALID -> new CertificationValid();
			case NO_TIME_CLASH -> new NoTimeClash();
			case DAILY_VISIT_CAP -> new DailyVisitCap(parseInt(parameter, DEFAULT_VISIT_CAP));
			case DAILY_HOURS_CAP -> new DailyHoursCap(parseDecimal(parameter, DEFAULT_HOURS_CAP));
			case CONTINUITY -> new Continuity();
			case SECTOR_BAND -> new SectorBand();
			case DIALECT_MATCH -> new DialectMatch();
			default -> new SpotCheck();
		};
	}

	/** A threshold somebody typed wrong falls back to the default instead of stopping rostering. */
	static int parseInt(String value, int fallback) {
		try {
			int parsed = Integer.parseInt(value == null ? "" : value.strip());
			return parsed > 0 ? parsed : fallback;
		}
		catch (NumberFormatException notANumber) {
			return fallback;
		}
	}

	static BigDecimal parseDecimal(String value, BigDecimal fallback) {
		try {
			BigDecimal parsed = new BigDecimal(value == null ? "" : value.strip());
			return parsed.signum() > 0 ? parsed : fallback;
		}
		catch (NumberFormatException notANumber) {
			return fallback;
		}
	}

	// ------------------------------------------------------------------ hard rules ---

	/** Somebody on approved leave that day cannot be sent, whatever else is true. */
	static final class NotOnLeave implements ReplacementRule {

		@Override
		public String code() {
			return NOT_ON_LEAVE;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.HARD;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			Optional<AbsenceReport> away = candidate.snapshot()
					.absenceKeepingAway(candidate.caregiverId(), candidate.slot().day());
			return away.map(absence -> RuleCheck.fail(this,
							"On leave %s to %s".formatted(absence.startDate(), absence.endDate())))
					.orElseGet(() -> RuleCheck.pass(this, "Not on leave"));
		}
	}

	/**
	 * Holds every certificate the care plan requires, still valid on the day of the visit.
	 * Somebody still onboarding has nothing published yet and is excluded here, as the
	 * caregiver table's status comment says.
	 */
	static final class CertificationValid implements ReplacementRule {

		@Override
		public String code() {
			return CERTIFICATION_VALID;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.HARD;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			if (candidate.candidate().onboarding()) {
				return RuleCheck.fail(this, "Still onboarding: no certificate published yet");
			}
			RosterSnapshot snapshot = candidate.snapshot();
			Set<Long> required = new TreeSet<>(snapshot.requiredTypes(candidate.slot().carePlanId()));
			if (required.isEmpty()) {
				return RuleCheck.notApplicable(this, "The care plan requires no certificate");
			}
			LocalDate day = candidate.slot().day();
			LocalDate earliest = null;
			Long binding = null;
			for (Long type : required) {
				Optional<LocalDate> until = snapshot.coveredUntil(candidate.caregiverId(), type);
				if (until.isEmpty()) {
					return RuleCheck.fail(this, "No %s certificate".formatted(snapshot.typeName(type)));
				}
				if (until.get().isBefore(day)) {
					return RuleCheck.fail(this,
							"%s expired %s".formatted(snapshot.typeName(type), until.get()));
				}
				if (earliest == null || until.get().isBefore(earliest)) {
					earliest = until.get();
					binding = type;
				}
			}
			return RuleCheck.pass(this, earliest.getYear() >= 9999
					? "%s does not expire".formatted(snapshot.typeName(binding))
					: "%s valid to %s".formatted(snapshot.typeName(binding), earliest));
		}
	}

	/** Not booked on another visit at an overlapping time. */
	static final class NoTimeClash implements ReplacementRule {

		@Override
		public String code() {
			return NO_TIME_CLASH;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.HARD;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			var slot = candidate.slot();
			return candidate.snapshot()
					.clash(candidate.caregiverId(), slot.start(), slot.effectiveEnd(), slot.visitId())
					.map(booking -> RuleCheck.fail(this, "Booked %s-%s with another elder"
							.formatted(booking.start().format(TIME), booking.end().format(TIME))))
					.orElseGet(() -> RuleCheck.pass(this, "Free %s-%s"
							.formatted(slot.start().format(TIME), slot.effectiveEnd().format(TIME))));
		}
	}

	/** No more than the institution's cap of visits in one day, this one included. */
	static final class DailyVisitCap implements ReplacementRule {

		private final int cap;

		DailyVisitCap(int cap) {
			this.cap = cap;
		}

		@Override
		public String code() {
			return DAILY_VISIT_CAP;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.HARD;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			int withThisOne = candidate.visitsThatDay() + 1;
			String detail = "%d / %d visits".formatted(withThisOne, cap);
			return withThisOne > cap ? RuleCheck.fail(this, detail) : RuleCheck.pass(this, detail);
		}
	}

	/** No more than the institution's cap of visit hours in one day, this one included. */
	static final class DailyHoursCap implements ReplacementRule {

		private final BigDecimal capHours;

		DailyHoursCap(BigDecimal capHours) {
			this.capHours = Objects.requireNonNull(capHours, "capHours");
		}

		@Override
		public String code() {
			return DAILY_HOURS_CAP;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.HARD;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			long minutes = candidate.minutesThatDay() + candidate.slot().length().toMinutes();
			BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
			String detail = "%s / %s hours".formatted(hours.toPlainString(),
					capHours.setScale(1, RoundingMode.HALF_UP).toPlainString());
			return hours.compareTo(capHours) > 0 ? RuleCheck.fail(this, detail) : RuleCheck.pass(this, detail);
		}
	}

	// ------------------------------------------------------------------ soft rules ---

	/** Has been to this elder before: the elder does not have to explain themselves again. */
	static final class Continuity implements ReplacementRule {

		@Override
		public String code() {
			return CONTINUITY;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.SOFT;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			int prior = candidate.priorVisits();
			return prior > 0
					? RuleCheck.pass(this, "%d earlier visit%s to this elder".formatted(prior, prior == 1 ? "" : "s"))
					: RuleCheck.fail(this, "Has not visited this elder before");
		}
	}

	/** Works in the elder's sector, which stands in for travel time: CareLink keeps no map. */
	static final class SectorBand implements ReplacementRule {

		@Override
		public String code() {
			return SECTOR_BAND;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.SOFT;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			String elderSector = candidate.elder().sector();
			String theirs = candidate.candidate().sector();
			if (isBlank(elderSector) || isBlank(theirs)) {
				return RuleCheck.notApplicable(this, "Sector not recorded");
			}
			return elderSector.strip().equalsIgnoreCase(theirs.strip())
					? RuleCheck.pass(this, "Works in " + theirs.strip())
					: RuleCheck.fail(this, "Works in %s, the elder is in %s".formatted(theirs.strip(), elderSector.strip()));
		}
	}

	/** Speaks one of the dialects the elder prefers. */
	static final class DialectMatch implements ReplacementRule {

		@Override
		public String code() {
			return DIALECT_MATCH;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.SOFT;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			Set<String> wanted = candidate.elder().dialects();
			if (wanted.isEmpty()) {
				return RuleCheck.notApplicable(this, "The elder has no preferred dialect");
			}
			Set<String> shared = new TreeSet<>(wanted);
			shared.retainAll(candidate.candidate().dialects());
			return shared.isEmpty()
					? RuleCheck.fail(this, "No shared dialect")
					: RuleCheck.pass(this, "Speaks " + String.join(", ", shared));
		}
	}

	/**
	 * Recent spot-check conclusions (UC-MG08). Only ever a soft rule: a conclusion feeds
	 * rostering but is never on its own the reason somebody is not sent.
	 */
	static final class SpotCheck implements ReplacementRule {

		@Override
		public String code() {
			return SPOT_CHECK;
		}

		@Override
		public RosteringConstraint.Kind kind() {
			return RosteringConstraint.Kind.SOFT;
		}

		@Override
		public RuleCheck check(CandidateContext candidate) {
			SpotCheckRecord record = candidate.snapshot().spotChecks(candidate.caregiverId());
			if (record.isEmpty()) {
				return RuleCheck.notApplicable(this, "No recent spot check");
			}
			return record.needsImprovement() > 0
					? RuleCheck.fail(this, "%d recent spot check%s needed improvement"
							.formatted(record.needsImprovement(), record.needsImprovement() == 1 ? "" : "s"))
					: RuleCheck.pass(this, "Recent spot checks met the standard");
		}
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}
}
