package sg.nus.carelink.profile.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;

/**
 * Persistence port for elder-family bindings.
 */
public interface ElderFamilyBindingRepository {

    Optional<ElderFamilyBinding> findById(Long id);

    /**
     * Lists all bindings belonging to one elder.
     */
    List<ElderFamilyBinding> findByElderId(Long elderId);

    /**
     * Lists one family's bindings so the domain can evaluate current read access.
     *
     * @param familyMemberId Family profile identifier, not an account identifier
     * @return Bindings in elder ID order, including inactive and expired relationships
     * @author Wang Zhili
     */
    List<ElderFamilyBinding> findByFamilyMemberId(Long familyMemberId);

    /**
     * Finds the unique relationship between one elder and one family member.
     */
    Optional<ElderFamilyBinding> findByElderIdAndFamilyMemberId(
            Long elderId,
            Long familyMemberId
    );

    ElderFamilyBinding save(
            ElderFamilyBinding elderFamilyBinding
    );
}
