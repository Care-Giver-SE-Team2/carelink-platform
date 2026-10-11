package sg.nus.carelink.visit.application;

/** An elder disputing a visit raises an incident in core's incident module (EL01). */
public interface ElderServiceDisputes {

	/** @return the incident's id */
	Long createElderServiceDispute(Long elderId, Long visitId, Long reportedByUserId, String description);

}
