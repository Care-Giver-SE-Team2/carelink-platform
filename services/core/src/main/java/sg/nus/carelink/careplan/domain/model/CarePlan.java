package sg.nus.carelink.careplan.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Domain model for care_plan.
 *
 * <p>identity.domain.model.AppUser is the template. Must not import JPA or Spring Data;
 * ArchUnit rejects the build if it does.
 */
public record CarePlan(
		Long id,
		Long elderId,
		Long createdByUserId,
		Long supersedesPlanId,
		Integer version,
		CarePlan.Status status,
		BigDecimal totalHours,
		LocalDateTime publishedAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDate startDate,
		LocalDate stopEffectiveDate,
		String stopReason,
		Long stoppedByUserId,
		LocalDateTime stoppedAt) {

	public CarePlan(
			Long id, Long elderId, Long createdByUserId, Long supersedesPlanId, Integer version,
			CarePlan.Status status, BigDecimal totalHours, LocalDateTime publishedAt,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		this(id, elderId, createdByUserId, supersedesPlanId, version, status, totalHours, publishedAt,
				createdAt, updatedAt, null, null, null, null, null);
	}

	public enum Status {
		DRAFT, PUBLISHED, SUPERSEDED, STOPPED
	}

	/**
	 * Opens a new, empty draft for an elder. An elder may have at most one open draft
	 * at a time — {@code latestForElder} (the elder's highest-version plan, or null if
	 * none exists yet) must not itself be a draft, or the manager already has one open
	 * and should keep editing it instead. The new draft is numbered one past the
	 * elder's latest plan and, when that plan is published, chains supersedesPlanId to
	 * it so the version history (screen 2a) can be read off the chain.
	 */
	public static CarePlan startDraft(Long elderId, Long createdByUserId, CarePlan latestForElder) {
		Objects.requireNonNull(elderId, "elderId");
		if (latestForElder != null && latestForElder.status() == Status.DRAFT) {
			throw new BusinessRuleViolation(
					"CARE_PLAN_DRAFT_ALREADY_OPEN",
					"Elder [%s] already has an open draft care plan".formatted(elderId));
		}
		int nextVersion = latestForElder == null ? 1 : latestForElder.version() + 1;
		Long supersedesPlanId = latestForElder != null && latestForElder.status() == Status.PUBLISHED
				? latestForElder.id()
				: null;
		return new CarePlan(
				null, elderId, createdByUserId, supersedesPlanId, nextVersion, Status.DRAFT,
				null, null, null, null);
	}

	/**
	 * Publishes this draft with the given start date and rolled-up weekly effort (the sum of
	 * its care_plan_node rows — never entered by hand, see the schema comment on total_hours).
	 * Only a draft may be published; publishing twice, or publishing a plan that was never
	 * opened as a draft, is a business rule violation. A start date is required — it is not
	 * asked for until publish time, matching how the rest of the tree isn't saved until then.
	 */
	public CarePlan publish(LocalDate startDate, BigDecimal totalHours) {
		if (status != Status.DRAFT) {
			throw new BusinessRuleViolation(
					"CARE_PLAN_NOT_DRAFT",
					"Care plan [%s] is not a draft".formatted(id));
		}
		if (startDate == null) {
			throw new BusinessRuleViolation(
					"CARE_PLAN_START_DATE_REQUIRED",
					"Care plan [%s] must have a start date to publish".formatted(id));
		}
		return new CarePlan(
				id, elderId, createdByUserId, supersedesPlanId, version, Status.PUBLISHED,
				totalHours, LocalDateTime.now(), createdAt, updatedAt,
				startDate, null, null, null, null);
	}

	/**
	 * The days this version's weekly schedule applies to. A published plan runs from its start
	 * date with no end; a stopped plan ends on its stop's effective date (the first day without
	 * visits); a superseded plan ends when its successor starts, so a new version published
	 * today with a start date next week leaves this one running until then. Empty for a draft,
	 * and for a plan published before start dates existed.
	 *
	 * @param successor the plan whose supersedesPlanId is this plan, if any
	 */
	public Optional<EffectivePeriod> effectivePeriod(CarePlan successor) {
		if (startDate == null) {
			return Optional.empty();
		}
		return switch (status) {
			case DRAFT -> Optional.empty();
			case PUBLISHED -> Optional.of(new EffectivePeriod(startDate, null));
			case STOPPED -> Optional.of(new EffectivePeriod(startDate, stopEffectiveDate));
			case SUPERSEDED -> Optional.of(new EffectivePeriod(startDate,
					successor == null || successor.startDate() == null ? startDate : successor.startDate()));
		};
	}

	/** Marks a previously published plan as superseded once the plan that replaces it publishes. */
	public CarePlan supersede() {
		return new CarePlan(
				id, elderId, createdByUserId, supersedesPlanId, version, Status.SUPERSEDED,
				totalHours, publishedAt, createdAt, updatedAt,
				startDate, null, null, null, null);
	}

	/**
	 * Ends an active plan early (screen 1n). Only a published plan can be stopped — a draft is
	 * discarded instead, and a superseded or already-stopped plan is no longer the elder's active
	 * one. The plan row and its node tree are kept; nothing is deleted, and a new plan can be
	 * drafted for the elder afterwards, same as after a normal publish.
	 */
	public CarePlan stop(LocalDate effectiveDate, String reason, Long stoppedByUserId) {
		if (status != Status.PUBLISHED) {
			throw new BusinessRuleViolation(
					"CARE_PLAN_NOT_PUBLISHED",
					"Care plan [%s] is not published".formatted(id));
		}
		Objects.requireNonNull(effectiveDate, "effectiveDate");
		if (reason == null || reason.isBlank()) {
			throw new BusinessRuleViolation(
					"CARE_PLAN_STOP_REASON_REQUIRED",
					"A reason is required to stop care plan [%s]".formatted(id));
		}
		return new CarePlan(
				id, elderId, createdByUserId, supersedesPlanId, version, Status.STOPPED,
				totalHours, publishedAt, createdAt, updatedAt,
				startDate, effectiveDate, reason, stoppedByUserId, LocalDateTime.now());
	}
}
