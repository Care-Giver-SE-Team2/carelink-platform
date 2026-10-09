package sg.nus.carelink.profile.domain.repository;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan.Lapse;

/**
 * SYS01: telling people a certificate is about to run out, or has. Who is told is already
 * decided on the {@link Lapse}; this port only puts the words in front of them.
 */
public interface CredentialExpiryAlert {

	/**
	 * @param caregiver whose certificate it is; their account is the caregiver recipient
	 * @param credentialName the credential type's name, e.g. "First aid"
	 * @return how many people were told
	 */
	int lapsed(Lapse lapse, Caregiver caregiver, String credentialName);
}
