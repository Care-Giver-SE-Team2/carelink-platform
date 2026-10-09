package sg.nus.carelink.profile.controller.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.profile.domain.model.ServiceApplication;

/** Family projection with an explicit UTC creation time and the original basic-details snapshot. */
public record FamilyServiceApplicationResponse(Long id, Long elderId, ElderBasicDetails elderSnapshot,
        List<String> careNeeds, String notes, ServiceApplication.Status status, OffsetDateTime createdAt) {
    public static FamilyServiceApplicationResponse from(ServiceApplication application) {
        return new FamilyServiceApplicationResponse(application.id(), application.elderId(),
                application.elderSnapshot(), application.careNeeds(), application.notes(), application.status(),
                application.createdAt().atOffset(ZoneOffset.UTC));
    }

    public record Page(List<FamilyServiceApplicationResponse> items, int page, int size, long totalElements) { }
}
