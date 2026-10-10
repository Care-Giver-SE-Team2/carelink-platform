package sg.nus.carelink.visit.controller.dto;

import java.time.LocalDateTime;

import sg.nus.carelink.visit.domain.model.Visit;

public record PendingElderVisitResponse(
        Long visitId,
        String serviceType,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime checkedOutAt,
        Visit.Status status
) {

    public static PendingElderVisitResponse from(
            Visit visit) {

        return new PendingElderVisitResponse(
                visit.id(),
                visit.serviceType(),
                visit.scheduledStart(),
                visit.scheduledEnd(),
                visit.checkedOutAt(),
                visit.status()
        );
    }
}