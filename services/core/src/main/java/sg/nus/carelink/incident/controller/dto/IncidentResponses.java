package sg.nus.carelink.incident.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.domain.model.EscalationChain;
import sg.nus.carelink.incident.domain.model.EscalationLevel;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.model.PageSlice;
import sg.nus.carelink.incident.domain.model.Playbook;

/**
 * The response shapes of UC-MG05.
 *
 * <p>Only the composite answers get a DTO. A single incident is returned as the domain
 * record, which is already flat and matches the contract; inventing a twin of it would mean
 * every new field had to be added twice. These three exist because they join things the
 * domain deliberately keeps apart: an incident with its timeline, a chain with its levels,
 * a contact attempt with the playbook it unlocked.
 */
public final class IncidentResponses {

	private IncidentResponses() {
	}

	/**
	 * {@code GET /api/incidents}: one page of the manager's queue.
	 *
	 * <p>A page and not a bare array, which is the shape the published contract has carried
	 * since before the endpoint existed. The four fields are the domain's {@link PageSlice}
	 * renamed to nothing at all - the translation happens once, in the adapter, and the web
	 * layer only chooses to publish it.
	 */
	public record Queue(List<Incident> items, int page, int size, long totalElements) {

		public static Queue of(PageSlice<Incident> slice) {
			return new Queue(slice.items(), slice.page(), slice.size(), slice.totalElements());
		}
	}

	/** {@code GET /api/incidents/{id}}: the incident and its full timeline. */
	public record Detail(
			Incident incident,
			String suggestedPlaybookCode,
			List<TimelineEntry> timeline) {

		public static Detail of(Incident incident, List<IncidentLog> entries) {
			return new Detail(
					incident,
					Playbook.forCategory(incident.category()).map(Playbook::code).orElse(null),
					entries.stream().map(TimelineEntry::of).toList());
		}
	}

	/**
	 * One line of the timeline.
	 *
	 * <p>Nothing is filtered out. A rejected take-over and a failed call to the family are
	 * exactly the entries that matter when somebody asks, months later, what was done.
	 */
	public record TimelineEntry(String actor, String action, String detail, LocalDateTime occurredAt) {

		static TimelineEntry of(IncidentLog entry) {
			return new TimelineEntry(entry.actor(), entry.action(), entry.detail(), entry.occurredAt());
		}
	}

	/** {@code GET /api/incidents/{id}/escalation-chain}: the plan, not a record of what happened. */
	public record Chain(
			Long incidentId,
			LocalDateTime assembledAt,
			Incident.Severity severity,
			String assembledFrom,
			List<Level> levels) {

		public static Chain of(EscalationChain chain) {
			return new Chain(
					chain.incidentId(),
					chain.assembledAt(),
					chain.severity(),
					chain.assembledFrom(),
					chain.levels().stream().map(Level::of).toList());
		}
	}

	/** One rung. A rung with no responder is still listed, so the gap is visible. */
	public record Level(
			int position,
			String tier,
			Long responderUserId,
			String responderName,
			long countdownMinutes,
			String state) {

		static Level of(EscalationLevel level) {
			return new Level(
					level.position(),
					level.tier().label(),
					level.responderUserId(),
					level.responder() == null ? null : level.responder().displayName(),
					level.countdown().toMinutes(),
					level.state().name());
		}
	}

	/** {@code POST /api/incidents/{id}/contact-attempts}. */
	public record ContactAttemptResult(
			boolean reachedTheFamily,
			String recorded,
			PlaybookView fallbackPlaybook) {

		public static ContactAttemptResult of(IncidentService.ContactOutcome outcome) {
			return new ContactAttemptResult(
					outcome.attempt().reachedTheFamily(),
					outcome.attempt().describe(),
					outcome.hasFallback() ? PlaybookView.of(outcome.suggestedPlaybook()) : null);
		}
	}

	/** {@code GET /api/incident-playbooks} and the fallback offered after a failed call. */
	public record PlaybookView(String code, Incident.Category category, String title, List<String> steps) {

		public static PlaybookView of(Playbook playbook) {
			return new PlaybookView(
					playbook.code(), playbook.category(), playbook.title(), playbook.steps());
		}
	}
}
