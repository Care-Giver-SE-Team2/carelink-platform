package sg.nus.carelink.report.infrastructure;

import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;

/** The names report's messages show, asked of core; a placeholder such as "Elder #3" when core has nobody by that id. */
@Component
class CoreNames {

    private final CoreApi core;

    CoreNames(CoreApi core) {
        this.core = core;
    }

    String elder(Long elderId) {
        try {
            return core.elderReportProfile(elderId).fullName();
        }
        catch (CoreNotFound notFound) {
            return "Elder #" + elderId;
        }
    }

    String caregiver(Long caregiverId) {
        return core.findCaregiverPublicProfile(caregiverId).map(CoreApi.CaregiverPublicProfile::fullName)
                .orElse("Caregiver #" + caregiverId);
    }

}
