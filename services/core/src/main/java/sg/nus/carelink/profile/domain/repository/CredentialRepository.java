package sg.nus.carelink.profile.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.profile.domain.model.Credential;

/**
 * Port for credential: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CredentialRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CredentialRepository {

	Optional<Credential> findById(Long id);

	/**
	 * Reads one caregiver's credentials in credential type and record ID order.
	 *
	 * @param caregiverId Caregiver profile identifier
	 * @return Credentials ordered by credentialTypeId then id, both ascending
	 * @author Wang Zhili
	 */
	List<Credential> findByCaregiverId(Long caregiverId);

	/** Every caregiver's credentials, whatever their status, in id order. */
	List<Credential> findAll();

	Credential save(Credential credential);
}
