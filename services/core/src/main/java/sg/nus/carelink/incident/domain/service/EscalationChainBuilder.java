package sg.nus.carelink.incident.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import sg.nus.carelink.incident.domain.model.EscalationChain;
import sg.nus.carelink.incident.domain.model.EscalationLevel;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.incident.domain.repository.ManagerDirectory;

/**
 * Assembles the escalation chain for one incident at one moment.
 *
 * <p>This is the Builder half of the design problem, and it exists because assembling the
 * chain and walking it change for different reasons. The walk is stable — offer it, take it
 * or pass it on — while the assembly rules are the part the institution rewrites: which
 * tiers a severity deserves and how long each level gets. Putting those rules inside the
 * handlers would mean every policy change edited the handling logic; putting them here means
 * the handlers never change.
 *
 * <p>The rule that varies the shape today: continuity costs time, because waiting for one
 * particular manager is slower than taking the first one free. A LOW severity incident is
 * not worth that wait, so its chain skips straight to any available manager. MEDIUM and HIGH
 * are, so they are offered first to whoever already knows the elder. Every chain keeps the
 * any-manager tier, which is what guarantees an incident can never be left with nobody.
 *
 * <p>Nothing here is persisted. Two calls a minute apart can legitimately produce different
 * chains — somebody closed another incident for this elder in between, or turned this one
 * down — which is why the result carries the moment it was built.
 */
public final class EscalationChainBuilder {

	private final Incident incident;
	private LocalDateTime at;
	private EscalationPolicy policy = EscalationPolicy.defaults();
	private ManagerDirectory directory;
	private IncidentRepository incidents;

	private EscalationChainBuilder(Incident incident) {
		this.incident = Objects.requireNonNull(incident, "incident");
	}

	public static EscalationChainBuilder forIncident(Incident incident) {
		return new EscalationChainBuilder(incident);
	}

	public EscalationChainBuilder at(LocalDateTime moment) {
		this.at = Objects.requireNonNull(moment, "at");
		return this;
	}

	public EscalationChainBuilder withPolicy(EscalationPolicy escalationPolicy) {
		this.policy = Objects.requireNonNull(escalationPolicy, "policy");
		return this;
	}

	public EscalationChainBuilder from(ManagerDirectory managerDirectory) {
		this.directory = Objects.requireNonNull(managerDirectory, "directory");
		return this;
	}

	/** Supplies the elder's incident history, which is what the continuity tier is built from. */
	public EscalationChainBuilder withHistory(IncidentRepository incidentRepository) {
		this.incidents = Objects.requireNonNull(incidentRepository, "incidents");
		return this;
	}

	/** The head of the handler chain: what {@code handle} is called on. */
	public ResponderHandler build() {
		requireComplete();
		ResponderHandler chain = new FamilyEscalationHandler();
		chain = new AnyManagerHandler(directory, chain);
		if (wantsContinuity()) {
			chain = new FamiliarManagerHandler(incidents, directory, chain);
		}
		return new AssignedResponderHandler(directory, chain);
	}

	/**
	 * The same chain expressed as a plan a manager can read, for
	 * {@code GET /api/incidents/{id}/escalation-chain}.
	 */
	public EscalationChain describe() {
		requireComplete();
		List<EscalationLevel> levels = build().survey(EscalationRequest.routing(incident, at, policy));
		return new EscalationChain(incident.id(), at, incident.severity(), assemblyReason(), levels);
	}

	private void requireComplete() {
		Objects.requireNonNull(at, "at: the chain is assembled for a moment, so the moment is required");
		Objects.requireNonNull(directory, "directory: the chain needs to know who can take an incident");
		Objects.requireNonNull(incidents, "incidents: the chain needs the elder's history for continuity");
	}

	private boolean wantsContinuity() {
		return incident.severity() != Incident.Severity.LOW;
	}

	private String assemblyReason() {
		List<String> parts = new ArrayList<>();
		parts.add("severity " + incident.severity());
		parts.add(wantsContinuity()
				? "continuity tier included"
				: "continuity skipped for low severity");
		parts.add("%d manager(s) enabled".formatted(directory.allManagers().size()));
		return String.join(", ", parts);
	}
}
