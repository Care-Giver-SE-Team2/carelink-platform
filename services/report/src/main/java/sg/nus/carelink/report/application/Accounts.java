package sg.nus.carelink.report.application;

/** The account behind the current request. */
public interface Accounts {

	/** The account id of the signed-in user, who signed in as {@code username}. */
	Long idOf(String username);

}
