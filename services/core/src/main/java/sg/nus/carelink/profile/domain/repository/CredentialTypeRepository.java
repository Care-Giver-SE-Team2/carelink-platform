package sg.nus.carelink.profile.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.profile.domain.model.CredentialType;

/**
 * Port for credential_type: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CredentialTypeRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CredentialTypeRepository {

	Optional<CredentialType> findById(Long id);

	/**
	 * Reads the requested credential types in one query.
	 *
	 * @param ids Credential type identifiers used by the result
	 * @return Matching types; empty when no IDs are requested
	 * @author Wang Zhili
	 */
	List<CredentialType> findByIds(Set<Long> ids);

	CredentialType save(CredentialType credentialType);
}
