package sg.nus.carelink.report.application;

/** Calling off a visit that will not take place, in visit. */
public interface VisitReassignment {

	void callOff(Long visitId, Change why);

	/** Who changed the visit and why; the absence and the rostering candidate are null here. */
	record Change(Long absenceId, Long byUserId, Long rosteringCandidateId, String reason) {
	}

}
