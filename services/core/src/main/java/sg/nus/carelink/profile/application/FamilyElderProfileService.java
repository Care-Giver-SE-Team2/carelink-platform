package sg.nus.carelink.profile.application;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** Family profile maintenance for existing elders, authorized through the current binding. */
@Service
@Transactional(readOnly = true)
public class FamilyElderProfileService {

    private final FamilyAccessQuery access;
    private final FamilyIdentityQuery identity;
    private final ElderRepository elders;
    private final ElderFamilyBindingRepository bindings;

    public FamilyElderProfileService(FamilyAccessQuery access, FamilyIdentityQuery identity,
            ElderRepository elders, ElderFamilyBindingRepository bindings) {
        this.access = access;
        this.identity = identity;
        this.elders = elders;
        this.bindings = bindings;
    }

    public List<FamilyElderProfile> list(String username) {
        var ids = access.readableElderIds(username);
        Long familyId = identity.requireFamilyMemberId(username);
        return elders.findByIds(ids).stream().map(elder -> project(elder, familyId)).toList();
    }

    public FamilyElderProfile get(String username, Long elderId) {
        access.requireReadableElder(username, elderId);
        return project(requireElder(elderId), identity.requireFamilyMemberId(username));
    }

    /** Recheck FULL permission on every save, including when a formerly editable page is still open. */
    @Transactional
    public FamilyElderProfile update(String username, Long elderId, ElderBasicDetails details) {
        access.requireWritableElder(username, elderId);
        Elder saved = elders.save(requireElder(elderId).withBasicDetails(details));
        return project(saved, identity.requireFamilyMemberId(username));
    }

    private Elder requireElder(Long elderId) {
        return elders.findById(elderId).orElseThrow(() -> new ResourceNotFound("Elder", elderId));
    }

    private FamilyElderProfile project(Elder elder, Long familyId) {
        var binding = bindings.findByElderIdAndFamilyMemberId(elder.id(), familyId)
                .orElseThrow(() -> new AccessDeniedException("A readable elder binding is required"));
        return FamilyElderProfile.from(elder, binding.accessScope());
    }
}
