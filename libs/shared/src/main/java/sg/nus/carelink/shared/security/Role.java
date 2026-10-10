package sg.nus.carelink.shared.security;

/**
 * System roles. Four kinds of user, four sets of screens; every authorisation
 * decision is made against this enum.
 *
 * <p>Stored in the database as text (user_role.role) rather than as an ordinal,
 * so that adding a role later cannot silently shift the meaning of existing rows.
 */
public enum Role {

	/** Supervisor: rostering, approvals, incident handling, full visibility. */
	MANAGER,

	/** Caregiver: carries out visits, raises incidents. */
	CAREGIVER,

	/** Family member: reads care records and reports for the elders they are entitled to see. */
	FAMILY,

	/** Elder: reads their own schedule and visits. */
	ELDER;

	/** Spring Security expects authorities to carry the ROLE_ prefix. */
	public String authority() {
		return "ROLE_" + name();
	}
}
