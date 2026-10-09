package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.model.ServiceApplicationPage;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.ServiceApplicationRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** Family submission and reads only. Management of the submitted request is a separate use case. */
@Service
@Transactional(readOnly = true)
public class FamilyServiceApplicationService {
    private final FamilyIdentityQuery identity;
    private final FamilyAccessQuery access;
    private final ElderRepository elders;
    private final ServiceApplicationRepository applications;
    private final Clock clock;

    public FamilyServiceApplicationService(FamilyIdentityQuery identity, FamilyAccessQuery access,
            ElderRepository elders, ServiceApplicationRepository applications, Clock clock) {
        this.identity = identity;
        this.access = access;
        this.elders = elders;
        this.applications = applications;
        this.clock = clock;
    }

    @Transactional
    public ServiceApplication submit(String username, Long elderId, List<String> careNeeds, String notes) {
        Long familyId = identity.requireFamilyMemberId(username);
        access.requireWritableElder(username, elderId);
        var elder = elders.findById(elderId).orElseThrow(() -> new ResourceNotFound("Elder", elderId));
        return applications.save(ServiceApplication.submit(familyId, elder, careNeeds, notes,
                LocalDateTime.now(clock.withZone(ZoneOffset.UTC))));
    }

    public ServiceApplicationPage list(String username, int page, int size) {
        Long familyId = identity.requireFamilyMemberId(username);
        // Filter by current access before counting or paging; revoked profiles must not leak through snapshots.
        return applications.findForApplicant(familyId, access.readableElderIds(username), page, size);
    }

    public ServiceApplication get(String username, Long id) {
        Long familyId = identity.requireFamilyMemberId(username);
        var application = applications.findById(id)
                .filter(item -> item.applicantFamilyMemberId().equals(familyId))
                .orElseThrow(() -> new ResourceNotFound("Service application", id));
        access.requireReadableElder(username, application.elderId());
        return application;
    }
}
