package sg.nus.carelink.visit.application;

/**
 * Whether a missed check-in's incident is the only thing that put a visit on hold, so that a late
 * check-in may resume it. core's incident module knows the visit's incidents.
 */
public interface MissedCheckInPauseEvidence {

	/** Whether {@code incidentId} is the visit's only incident, raised by the missed check-in scan. */
	boolean isSoleIncident(Long elderId, Long visitId, Long incidentId);

}
