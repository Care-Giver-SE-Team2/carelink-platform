package sg.nus.carelink.report.controller.dto;

import java.time.LocalDateTime;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

public record ValueAddedServiceRequestResponse(
        Long id,
        Long elderId,
        Long valueAddedServiceId,
        String serviceName,
        Long requestedByFamilyMemberId,
        Long approvingFamilyMemberId,
        Long visitId,
        LocalDateTime requestedSchedule,
        String specialInstructions,
        ValueAddedServiceRequest.Status status,
        LocalDateTime decidedAt,
        LocalDateTime createdAt) {

    public static ValueAddedServiceRequestResponse from(
            ValueAddedServiceRequest request,
            ValueAddedService service) {
        return new ValueAddedServiceRequestResponse(
                request.id(), request.elderId(), request.valueAddedServiceId(), service.name(),
                request.requestedByFamilyMemberId(), request.approvingFamilyMemberId(), request.visitId(),
                request.requestedSchedule(), request.specialInstructions(), request.status(),
                request.decidedAt(), request.createdAt());
    }
}
