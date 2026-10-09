package sg.nus.carelink.profile.domain.repository;

import java.util.Optional;

import sg.nus.carelink.profile.domain.model.FamilyMember;

/**
 * Port for family_member: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.FamilyMemberRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface FamilyMemberRepository {

	Optional<FamilyMember> findById(Long id);

	/**
	 * Find the family profile linked to an account.
	 *
	 * @param userId Account identifier
	 * @return The linked family profile, or empty if none exists
	 *
	 * @author Wang Zhili
	 */
	Optional<FamilyMember> findByUserId(Long userId);

	FamilyMember save(FamilyMember familyMember);
}
