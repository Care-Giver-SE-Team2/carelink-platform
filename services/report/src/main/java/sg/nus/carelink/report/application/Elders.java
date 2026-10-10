package sg.nus.carelink.report.application;

/** The elder profiles core keeps for elder accounts. */
public interface Elders {

	/**
	 * The elder an elder account belongs to.
	 *
	 * @throws sg.nus.carelink.shared.error.ResourceNotFound when the account has no elder profile
	 */
	Long requireElderIdOfUser(Long userId);

}
