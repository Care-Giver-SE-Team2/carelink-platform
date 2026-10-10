package sg.nus.carelink.profile.controller.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.NeedProgress;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.Outcome;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.Progress;

/**
 * Family projection with an explicit UTC creation time and the original basic-details snapshot.
 * {@code status} is what was recorded (SUBMITTED, or DECLINED by a manager); {@code outcome} is what
 * the family is told, worked out from the care plan: PLANNED once every activity is in a published
 * version, which outranks a decline. {@code needs} gives each activity's first planning version.
 */
public record FamilyServiceApplicationResponse(Long id, Long elderId, ElderBasicDetails elderSnapshot,
        List<String> careNeeds, String notes, ServiceApplication.Status status, OffsetDateTime createdAt,
        Outcome outcome, List<NeedProgress> needs, String declineReason, OffsetDateTime declinedAt) {
    public static FamilyServiceApplicationResponse from(ServiceApplication application, Progress progress) {
        var decline = application.decline();
        return new FamilyServiceApplicationResponse(application.id(), application.elderId(),
                application.elderSnapshot(), application.careNeeds(), application.notes(), application.status(),
                application.createdAt().atOffset(ZoneOffset.UTC), progress.outcome(), progress.needs(),
                decline == null ? null : decline.reason(),
                decline == null ? null : decline.at().atOffset(ZoneOffset.UTC));
    }

    public record Page(List<FamilyServiceApplicationResponse> items, int page, int size, long totalElements) { }
}
