package sg.nus.carelink.incident.domain.model;

/**
 * The kinds of responder an escalation chain can be built from, in the order they are
 * tried.
 *
 * <p>This is a tier, not a person. Who fills a tier is worked out when the incident is
 * routed, which is why the chain is assembled per incident rather than written down once:
 * it depends on the severity, on who has handled this elder before, and on who has already
 * turned this particular incident down. None of that is knowable when the code is written.
 *
 * <p>{@link #FAMILY_ESCALATION} is the terminal tier. It never assigns anyone — it is what
 * happens when the chain is exhausted, and it exists as a tier so that "nobody took it
 * over" is an ordinary step of the chain rather than a null check at the end of it.
 */
public enum EscalationTier {

	/** Whoever is already named on the incident. Tried first so a re-scan does not move a live case. */
	ASSIGNED_RESPONDER("Assigned responder"),

	/** The manager who handled this elder's last incident. Continuity: they already know the history. */
	FAMILIAR_MANAGER("Manager who knows this elder"),

	/** Any enabled manager. The guaranteed fallback, so an incident can never end up with nobody. */
	ANY_MANAGER("Any manager"),

	/** Terminal: mark the incident unresolved, pin it to the top-level view, tell the family. */
	FAMILY_ESCALATION("Family escalation");

	private final String label;

	EscalationTier(String label) {
		this.label = label;
	}

	/** Human-readable name, returned by the API so the front end does not hard-code the enum. */
	public String label() {
		return label;
	}

	public boolean isTerminal() {
		return this == FAMILY_ESCALATION;
	}
}
