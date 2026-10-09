package sg.nus.carelink.rostering.domain.repository;

import sg.nus.carelink.rostering.domain.model.RosterChange;

/**
 * The audit trail of UC-MG04 alternative 4a: when nobody answered and the institution's
 * default plan ran, that fact is logged where audits are read, not only kept on the change.
 */
public interface RosterAudit {

	void defaultPlanApplied(RosterChange change, String detail);
}
