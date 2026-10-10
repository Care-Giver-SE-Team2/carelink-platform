package sg.nus.carelink.profile.domain.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * How far a family's request for care has been answered, worked out from the elder's care plan
 * versions rather than stored. A version is never edited once published, so "was this activity
 * in a version in force on or after the day they applied?" always has the same answer: nothing
 * done later can make a planned activity unplanned, and an activity already in the plan when the
 * family applied counts straight away.
 *
 * <p>A request is PLANNED once every activity it asked for is planned, DECLINED if the manager
 * declined it before that, and SUBMITTED otherwise. Planned outranks declined: if the care was put
 * in the plan anyway, that is what the family is told. A need that is not an activity code (free
 * text from before the forms offered the catalog only) can never be planned, so such a request
 * stays SUBMITTED until it is declined.
 */
public final class ServiceApplicationProgress {

	private ServiceApplicationProgress() {
	}

	public enum Outcome { SUBMITTED, PLANNED, DECLINED }

	/**
	 * One issued care plan version: the days it is in force ({@code until} exclusive, null while
	 * open-ended) and the activity codes of its tasks.
	 */
	public record PlanVersion(int version, LocalDate from, LocalDate until, Set<String> activityCodes) {

		public PlanVersion {
			activityCodes = Set.copyOf(activityCodes);
		}

		/** In force for at least one day on or after {@code day}. */
		boolean inForceOnOrAfter(LocalDate day) {
			boolean someDays = until == null || until.isAfter(from);
			return someDays && (until == null || until.isAfter(day));
		}
	}

	/** One requested activity: the first version that planned it, if any. */
	public record NeedProgress(String need, Integer plannedVersion, LocalDate plannedFrom) {

		public boolean planned() {
			return plannedVersion != null;
		}
	}

	public record Progress(Outcome outcome, List<NeedProgress> needs) {

		public Progress {
			needs = List.copyOf(needs);
		}
	}

	/**
	 * @param needs the care needs the family asked for, as stored on the application
	 * @param submittedOn the day they applied, in Singapore
	 * @param declined whether the manager has declined the request
	 * @param versions every issued version of the elder's plan, in any order
	 */
	public static Progress of(List<String> needs, LocalDate submittedOn, boolean declined, List<PlanVersion> versions) {
		List<PlanVersion> oldestFirst = versions.stream()
				.filter(version -> version.inForceOnOrAfter(submittedOn))
				.sorted(Comparator.comparingInt(PlanVersion::version))
				.toList();
		List<NeedProgress> progress = needs.stream().map(need -> {
			Optional<PlanVersion> first = oldestFirst.stream()
					.filter(version -> version.activityCodes().contains(need))
					.findFirst();
			return new NeedProgress(need, first.map(PlanVersion::version).orElse(null),
					first.map(PlanVersion::from).orElse(null));
		}).toList();
		boolean allPlanned = !progress.isEmpty() && progress.stream().allMatch(NeedProgress::planned);
		Outcome outcome = allPlanned ? Outcome.PLANNED : declined ? Outcome.DECLINED : Outcome.SUBMITTED;
		return new Progress(outcome, progress);
	}

	/** A request whose care is already planned has been answered; it can no longer be declined. */
	public static void requireDeclinable(Progress progress) {
		if (progress.outcome() == Outcome.PLANNED) {
			throw new BusinessRuleViolation("SERVICE_APPLICATION_ALREADY_PLANNED",
					"Everything this application asked for is already in the care plan");
		}
	}
}
