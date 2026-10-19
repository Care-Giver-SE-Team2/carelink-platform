package sg.nus.carelink.report.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A value-added request offered to an Open Platform provider because no caregiver was free for it
 * (docs/section5/decisions.md, D7). It is report's, not a visit: provider staff never check in. The
 * provider answers it through the job API before {@link #respondBy}; a declined job, or one left
 * unanswered, goes to a manager as a visit nobody holds.
 *
 * <p>Every change returns the job with {@link #version} one higher, which {@code ProviderJobUpdated} and
 * the provider's webhooks carry. Times are Singapore wall-clock time, as report keeps them.
 */
public record ProviderJob(
        Long id,
        Long providerId,
        Long requestId,
        String serviceName,
        String district,
        String areaName,
        String elderSummary,
        String languages,
        String bookedBy,
        LocalDateTime scheduledStart,
        int durationMinutes,
        Status status,
        LocalDateTime respondBy,
        LocalDateTime dispatchedAt,
        LocalDateTime acceptedAt,
        Decline decline,
        Completion completion,
        LocalDateTime cancelledAt,
        String cancelReason,
        int version) {

    /** How long a provider has to answer, unless the start is nearer (see {@link #respondByFor}). */
    public static final Duration ANSWER_WITHIN = Duration.ofHours(24);

    /** The latest a provider may answer before the start, so that a manager still has time. */
    public static final Duration ANSWER_BEFORE_START = Duration.ofHours(4);

    /** A request starting sooner than this is not offered: the provider would have under an hour. */
    public static final Duration MIN_NOTICE = ANSWER_BEFORE_START.plusHours(1);

    public static final String EXPIRED_REASON = "The provider did not answer in time; CareLink has made other arrangements.";

    public ProviderJob {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(scheduledStart, "scheduledStart");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(respondBy, "respondBy");
        Objects.requireNonNull(dispatchedAt, "dispatchedAt");
    }

    /** Whether a request starting at {@code start} can still be offered to a provider at {@code now}. */
    public static boolean offerable(LocalDateTime start, LocalDateTime now) {
        return !start.isBefore(now.plus(MIN_NOTICE));
    }

    /** 24 hours after dispatch, but no later than 4 hours before the start. */
    public static LocalDateTime respondByFor(LocalDateTime start, LocalDateTime dispatchedAt) {
        LocalDateTime inADay = dispatchedAt.plus(ANSWER_WITHIN);
        LocalDateTime beforeStart = start.minus(ANSWER_BEFORE_START);
        return inADay.isBefore(beforeStart) ? inADay : beforeStart;
    }

    /** A new job for the provider, with what it decides on. */
    @SuppressWarnings("java:S107") // one argument per thing the provider is told
    public static ProviderJob dispatched(Long providerId, Long requestId, String serviceName, String district,
            String areaName, String elderSummary, String languages, String bookedBy, LocalDateTime start,
            int durationMinutes, LocalDateTime now) {
        if (!offerable(start, now)) {
            throw new IllegalArgumentException("Too near the start to offer to a provider: " + start);
        }
        return new ProviderJob(null, providerId, requestId, serviceName, district, areaName, elderSummary,
                languages, bookedBy, start, durationMinutes, Status.NEW, respondByFor(start, now), now, null, null,
                null, null, null, 0);
    }

    /** The provider takes the job on. Only a NEW job, and only until {@link #respondBy}. */
    public ProviderJob accepted(LocalDateTime now) {
        requireAnswerable(now);
        return new ProviderJob(id, providerId, requestId, serviceName, district, areaName, elderSummary, languages,
                bookedBy, scheduledStart, durationMinutes, Status.ACCEPTED, respondBy, dispatchedAt, now, null,
                null, null, null, version + 1);
    }

    /** The provider turns the job down. Only a NEW job, and only until {@link #respondBy}. */
    public ProviderJob declined(Decline why) {
        Objects.requireNonNull(why, "why");
        requireAnswerable(why.at());
        return new ProviderJob(id, providerId, requestId, serviceName, district, areaName, elderSummary, languages,
                bookedBy, scheduledStart, durationMinutes, Status.DECLINED, respondBy, dispatchedAt, null, why,
                null, null, null, version + 1);
    }

    /** The provider reports the work done. Only an ACCEPTED job, with times that have passed. */
    public ProviderJob completed(Completion done) {
        Objects.requireNonNull(done, "done");
        requireNotCancelled();
        if (status != Status.ACCEPTED) {
            throw notActionable();
        }
        if (!done.actualEnd().isAfter(done.actualStart()) || done.actualEnd().isAfter(done.at())) {
            throw new InvalidTimes();
        }
        return new ProviderJob(id, providerId, requestId, serviceName, district, areaName, elderSummary, languages,
                bookedBy, scheduledStart, durationMinutes, Status.COMPLETED, respondBy, dispatchedAt, acceptedAt,
                null, done, null, null, version + 1);
    }

    /** CareLink or the family calls the job off, with the reason the provider is given. */
    public ProviderJob cancelled(String reason, LocalDateTime now) {
        if (!open()) {
            throw notActionable();
        }
        return new ProviderJob(id, providerId, requestId, serviceName, district, areaName, elderSummary, languages,
                bookedBy, scheduledStart, durationMinutes, Status.CANCELLED, respondBy, dispatchedAt, acceptedAt,
                null, null, now, reason, version + 1);
    }

    /** A NEW job whose {@link #respondBy} has passed: called off, for a manager to take over. */
    public ProviderJob expired(LocalDateTime now) {
        if (status != Status.NEW || !now.isAfter(respondBy)) {
            throw new IllegalStateException("Job " + id + " has not run out of time");
        }
        return cancelled(EXPIRED_REASON, now);
    }

    /** NEW or ACCEPTED: the provider may still act on it, and CareLink may still call it off. */
    public boolean open() {
        return status == Status.NEW || status == Status.ACCEPTED;
    }

    /** {@link #respondBy} as the job API shows it: null once the job is not NEW. */
    public LocalDateTime respondByShown() {
        return status == Status.NEW ? respondBy : null;
    }

    private void requireAnswerable(LocalDateTime now) {
        requireNotCancelled();
        if (status != Status.NEW) {
            throw notActionable();
        }
        if (now.isAfter(respondBy)) {
            throw new BusinessRuleViolation("RESPONSE_DEADLINE_PASSED",
                    "The time to answer this job has passed; CareLink has made other arrangements.");
        }
    }

    private void requireNotCancelled() {
        if (status == Status.CANCELLED) {
            throw new BusinessRuleViolation("JOB_CANCELLED",
                    cancelReason == null ? "The job was cancelled." : cancelReason);
        }
    }

    private BusinessRuleViolation notActionable() {
        return new BusinessRuleViolation("JOB_NOT_ACTIONABLE", "The job is " + status + ".");
    }

    public enum Status {
        NEW, ACCEPTED, DECLINED, COMPLETED, CANCELLED
    }

    public enum DeclineReason {
        OUTSIDE_HOURS, NO_PRACTITIONER, OUTSIDE_AREA, OTHER
    }

    /** Why the provider turned the job down; a note is required for OTHER. */
    public record Decline(DeclineReason reason, String note, LocalDateTime at) {
        public Decline {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(at, "at");
            note = note == null || note.isBlank() ? null : note.trim();
            if (reason == DeclineReason.OTHER && note == null) {
                throw new MissingNote();
            }
        }
    }

    /** When the work really started and ended, as the provider reports it. */
    public record Completion(LocalDateTime actualStart, LocalDateTime actualEnd, String note, LocalDateTime at) {
        public Completion {
            Objects.requireNonNull(actualStart, "actualStart");
            Objects.requireNonNull(actualEnd, "actualEnd");
            Objects.requireNonNull(at, "at");
            note = note == null || note.isBlank() ? null : note.trim();
        }
    }

    /** {@code INVALID_TIMES}: the end is not after the start, or either is in the future. */
    public static final class InvalidTimes extends RuntimeException {
        public InvalidTimes() {
            super("actualEnd must be after actualStart, and neither may be in the future.");
        }
    }

    /** A decline for OTHER without a note: a validation failure on {@code note}. */
    public static final class MissingNote extends RuntimeException {
        public MissingNote() {
            super("Required when the reason is OTHER.");
        }
    }
}
