package sg.nus.carelink.profile.domain.repository;

import java.util.Optional;
import java.util.Set;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.model.ServiceApplicationPage;

/** New service requests are isolated from the legacy intake/elder-creation approval queue. */
public interface ServiceApplicationRepository {
    ServiceApplication save(ServiceApplication application);
    Optional<ServiceApplication> findById(Long id);
    ServiceApplicationPage findForApplicant(Long familyId, Set<Long> readableElderIds, int page, int size);
}
