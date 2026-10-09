package sg.nus.carelink.profile.application;

/**
 * Resolves a current, enabled family account to its family profile ID.
 *
 * @author Wang Zhili
 */
public interface FamilyIdentityQuery {

	Long requireFamilyMemberId(String authenticatedUsername);
}
