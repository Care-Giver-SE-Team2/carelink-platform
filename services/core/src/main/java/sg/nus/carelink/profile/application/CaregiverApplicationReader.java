package sg.nus.carelink.profile.application;

import java.util.List;
import sg.nus.carelink.profile.domain.model.CaregiverApplication;

/** Manager owner supplies its Controller/UI. Every call rechecks manager identity. */
public interface CaregiverApplicationReader {
    record Page(List<CaregiverApplication> items, int page, int size, long total) { }
    Page pendingApplications(String managerUsername, int page, int size);
    CaregiverApplication.View applicationForManager(String managerUsername, Long applicationId);
}
