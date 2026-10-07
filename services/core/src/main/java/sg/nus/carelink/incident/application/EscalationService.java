package sg.nus.carelink.incident.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.incident.domain.model.EscalationChain;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.repository.IncidentAlert;
import sg.nus.carelink.incident.domain.repository.IncidentLogRepository;
import sg.nus.carelink.incident.domain.repository.ManagerDirectory;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.incident.domain.service.EscalationChainBuilder;
import sg.nus.carelink.incident.domain.service.EscalationOutcome;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.domain.service.EscalationRequest;
import sg.nus.carelink.incident.domain.service.ResponderHandler;

/**
 * Routing: who holds an incident, and what happens when they do not answer.
 *
 * <p>The place where UC-MG05 and UC-SYS02 meet. Both need exactly the same two steps —
 * assemble a chain for this incident at this moment, walk it, write down what happened — so
 * they share one service rather than two near-identical copies. The decision of <em>who</em>
 * is not made here: that is the chain's job, and this class only applies the answer and
 * records it.
 *
 * <p>Every path through this class writes to the timeline, including the ones where nobody
 * took the incident. The use case's failure post-condition depends on it: "时间线保留全部
 * 尝试记录，包括未成功的接管与联络".
 */
@Service
@Transactional
public class EscalationService {

	private final IncidentRepository incidents;
	private final IncidentLogRepository timeline;
	private final ManagerDirectory directory;
	private final IncidentAlert alert;
	private final EscalationPolicy policy;
	private final Clock clock;

	EscalationService(
			IncidentRepository incidents,
			IncidentLogRepository timeline,
			ManagerDirectory directory,
			IncidentAlert alert,
			EscalationPolicy policy,
			Clock clock) {

		this.incidents = incidents;
		this.timeline = timeline;
		this.directory = directory;
		this.alert = alert;
		this.policy = policy;
		this.clock = clock;
	}

	/**
	 * Gives a freshly raised incident its first responder and starts the countdown.
	 *
	 * <p>Called immediately after the incident is saved, from whichever module raised it.
	 * No incident is allowed to exist without a named responder and a deadline — that is the
	 * pre-condition UC-MG05 assumes, and the reason an elder's SOS cannot simply be inserted
	 * and left.
	 */
	public Incident routeNewIncident(Incident saved) {
		LocalDateTime now = now();

		// Everyone who could act hears about it first, in the same second, before the chain
		// decides anything. Who finds out and who is answerable are different questions, and
		// nobody should wait on the second to be told the first.
		int told = alert.broadcastRaised(saved);
		timeline.save(IncidentLog.systemEntry(saved.id(), IncidentLog.Action.BROADCAST,
				"%d recipient(s): every manager, the family bound to this elder, and the caregiver "
						.formatted(told) + "who last visited", now));

		EscalationOutcome outcome = chainFor(saved, now).handle(EscalationRequest.routing(saved, now, policy));
		return applyOutcome(saved, outcome, now, IncidentLog.SYSTEM_ACTOR, "first responder");
	}

	/**
	 * Moves the incident to the next level of the chain.
	 *
	 * <p>Driven by the scheduled scan when a countdown expires, and available to a manager
	 * who wants to escalate early. The responder who is being escalated past keeps their
	 * record: responsibility in this use case accumulates rather than transfers.
	 */
	public Incident escalate(Incident incident, String reason, String actor) {
		LocalDateTime now = now();

		timeline.save(IncidentLog.entry(incident.id(), actor, IncidentLog.Action.ESCALATED,
				"from responder %s: %s".formatted(incident.responderUserId(), reason), now));

		EscalationRequest request =
				EscalationRequest.afterTimeout(incident, now, policy, respondersSoFar(incident.id()));
		EscalationOutcome outcome = chainFor(incident, now).handle(request);
		return applyOutcome(incident, outcome, now, actor, "escalated: " + reason);
	}

	/**
	 * Re-assembles the chain after the severity changed, without disturbing the current
	 * responder or opening a second incident.
	 *
	 * <p>UC-MG05 5a: "主管提升严重度，系统按新严重度重新装配升级链，原时间线延续不另起".
	 * The first link of the chain is the responder already named on the incident, so a
	 * manager who is handling the case keeps it and only the deadline is recalculated.
	 */
	public Incident reassembleAfterSeverityChange(Incident incident) {
		if (!incident.awaitingTakeOver()) {
			return incident;
		}
		LocalDateTime now = now();
		EscalationOutcome outcome = chainFor(incident, now)
				.handle(EscalationRequest.routing(incident, now, policy));
		return applyOutcome(incident, outcome, now, IncidentLog.SYSTEM_ACTOR, "chain rebuilt for new severity");
	}

	/**
	 * Re-reads one incident and escalates it only if its countdown really has expired.
	 *
	 * <p>The per-incident half of the UC-SYS02 sweep, and the reason it lives here rather
	 * than on the scan: {@code REQUIRES_NEW} is applied by the proxy around this bean, so the
	 * caller has to be a different bean for each incident to get its own transaction. A
	 * rollback then affects the one incident that failed and no other.
	 *
	 * <p>The second read is what implements alternative 2a. A responder can take the incident
	 * over in the window between the sweep selecting it and the sweep reaching it; when that
	 * happens the escalation is abandoned and the near miss is written to the timeline,
	 * because an escalation that almost happened is part of what went on.
	 *
	 * @return true when the incident actually moved to another level
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean escalateIfStillOverdue(Long incidentId, LocalDateTime scanStartedAt) {
		Incident current = incidents.findById(incidentId).orElse(null);
		if (current == null) {
			return false;
		}

		LocalDateTime now = now();
		if (!current.isOverdue(now)) {
			timeline.save(IncidentLog.systemEntry(incidentId, IncidentLog.Action.ESCALATION_CANCELLED,
					"countdown had expired when the sweep started at %s, but the incident is now %s"
							.formatted(scanStartedAt, current.status()),
					now));
			return false;
		}

		escalate(current, "response countdown expired", IncidentLog.SYSTEM_ACTOR);
		return true;
	}

	/** The chain as a plan, for the manager's screen. Changes nothing. */
	@Transactional(readOnly = true)
	public EscalationChain describeChain(Incident incident) {
		return builder(incident, now()).describe();
	}

	// ------------------------------------------------------------------- internals ---

	private Incident applyOutcome(
			Incident incident, EscalationOutcome outcome, LocalDateTime now, String actor, String narrative) {

		if (outcome.assigned()) {
			Incident assigned = incidents.save(
					incident.assignTo(outcome.responder().userId(), outcome.respondBy(now)));
			timeline.save(IncidentLog.assignment(
					assigned.id(), actor, outcome.responder().userId(),
					"%s - %s".formatted(narrative, outcome.describeRoute()), now));
			// Only the two people whose responsibility changed. The rest already know.
			alert.handedOver(assigned, incident.responderUserId(), assigned.responderUserId());
			return assigned;
		}

		Incident unresolved = incidents.save(incident.markUnresolvedEscalated());
		timeline.save(IncidentLog.entry(unresolved.id(), actor, IncidentLog.Action.CHAIN_EXHAUSTED,
				"%s - %s; pinned for the family".formatted(narrative, outcome.describeRoute()), now));
		alert.chainExhausted(unresolved);
		return unresolved;
	}

	private ResponderHandler chainFor(Incident incident, LocalDateTime at) {
		return builder(incident, at).build();
	}

	private EscalationChainBuilder builder(Incident incident, LocalDateTime at) {
		return EscalationChainBuilder.forIncident(incident)
				.at(at)
				.withPolicy(policy)
				.from(directory)
				.withHistory(incidents);
	}

	/** Everyone this incident has already been handed to, read back from the timeline. */
	private Set<Long> respondersSoFar(Long incidentId) {
		List<IncidentLog> entries = timeline.findTimeline(incidentId);
		Set<Long> responders = new LinkedHashSet<>();
		for (IncidentLog entry : entries) {
			entry.assignedResponderId().ifPresent(responders::add);
		}
		return responders;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
