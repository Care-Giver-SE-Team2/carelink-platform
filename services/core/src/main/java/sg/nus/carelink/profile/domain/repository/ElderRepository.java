package sg.nus.carelink.profile.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.profile.domain.model.Elder;

/**
 * Port for elder: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.ElderRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface ElderRepository {

    Optional<Elder> findById(Long id);

    List<Elder> findAll();

    /**
     * Finds the supplied elder IDs in ascending ID order.
     *
     * @param elderIds IDs selected by the calling use case
     * @return Matching elder profiles
     * @author Wang Zhili
     */
    List<Elder> findByIds(Set<Long> elderIds);

    /**
     * Finds the elder profile associated with an application user account.
     *
     * @param userId app_user.id
     * @return the elder linked to the user account, if one exists
     */
    Optional<Elder> findByUserId(Long userId);

    /** Elders registered at a postcode, for the one-elder-one-record check. */
    List<Elder> findByPostalCode(String postalCode);

    Elder save(Elder elder);
}
