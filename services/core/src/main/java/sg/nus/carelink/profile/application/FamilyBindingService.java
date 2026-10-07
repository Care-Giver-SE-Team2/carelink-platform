package sg.nus.carelink.profile.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.identity.domain.repository.AppUserRepository;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * Application service for EL04 elder-side family binding management.
 */
@Service
@Transactional
public class FamilyBindingService {

    private final ElderRepository elders;
    private final FamilyMemberRepository familyMembers;
    private final ElderFamilyBindingRepository bindings;
    private final AppUserRepository users;

    public FamilyBindingService(
            ElderRepository elders,
            FamilyMemberRepository familyMembers,
            ElderFamilyBindingRepository bindings,
            AppUserRepository users) {

        this.elders = elders;
        this.familyMembers = familyMembers;
        this.bindings = bindings;
        this.users = users;
    }

    /**
     * Lists all family bindings belonging to the currently authenticated elder.
     */
    @Transactional(readOnly = true)
    public List<ElderFamilyBinding> listForElderUser(
            Long elderUserId) {

        Elder elder =
                requireElderByUserId(elderUserId);

        return bindings.findByElderId(
                elder.id()
        );
    }

    /**
     * Creates or re-opens a family binding request.
     */
    public ElderFamilyBinding create(
            Long elderUserId,
            String familyUsername,
            ElderFamilyBinding.Relationship relationship,
            boolean primaryContact,
            ElderFamilyBinding.AccessScope accessScope) {

        Elder elder =
                requireElderByUserId(elderUserId);

        String username =
                familyUsername.trim();

        AppUser familyUser = users
                .findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFound(
                                "Family account",
                                username
                        )
                );

        if (!familyUser.enabled()
                || !familyUser.hasRole(Role.FAMILY)) {

            throw new ResourceNotFound(
                    "Family account",
                    username
            );
        }

        FamilyMember familyMember =
                familyMembers
                        .findByUserId(
                                familyUser.id()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFound(
                                        "Family member for user",
                                        familyUser.id()
                                )
                        );

        var existing =
                bindings.findByElderIdAndFamilyMemberId(
                        elder.id(),
                        familyMember.id()
                );

        if (existing.isPresent()) {
            ElderFamilyBinding current =
                    existing.get();

            if (current.status()
                    == ElderFamilyBinding.Status.REJECTED
                    || current.status()
                    == ElderFamilyBinding.Status.REVOKED) {

                return bindings.save(
                        current.requestAgain(
                                relationship,
                                primaryContact,
                                accessScope
                        )
                );
            }

            throw new BusinessRuleViolation(
                    "FAMILY_BINDING_ALREADY_EXISTS",
                    "This family member is already linked or awaiting confirmation."
            );
        }

        ElderFamilyBinding binding =
                ElderFamilyBinding.request(
                        elder.id(),
                        familyMember.id(),
                        relationship,
                        primaryContact,
                        accessScope
                );

        return bindings.save(binding);
    }

    /**
     * Revokes one binding belonging to the current elder.
     */
    public ElderFamilyBinding revoke(
            Long elderUserId,
            Long bindingId) {

        Elder elder =
                requireElderByUserId(
                        elderUserId
                );

        ElderFamilyBinding binding =
                bindings.findById(bindingId)
                        .orElseThrow(() ->
                                new ResourceNotFound(
                                        "Family binding",
                                        bindingId
                                )
                        );

        /*
         * Do not reveal whether another elder's binding exists.
         */
        if (!binding.belongsToElder(
                elder.id())) {

            throw new ResourceNotFound(
                    "Family binding",
                    bindingId
            );
        }

        return bindings.save(
                binding.revoke()
        );
    }

    @Transactional(readOnly = true)
    public FamilyMember requireFamilyMember(
            Long familyMemberId) {

        return familyMembers
                .findById(familyMemberId)
                .orElseThrow(() ->
                        new ResourceNotFound(
                                "Family member",
                                familyMemberId
                        )
                );
    }

    private Elder requireElderByUserId(
            Long userId) {

        return elders
                .findByUserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFound(
                                "Elder for user",
                                userId
                        )
                );
    }
}