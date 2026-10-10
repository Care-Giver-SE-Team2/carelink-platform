package sg.nus.carelink.report.controller.dto;

import java.time.LocalDateTime;

import sg.nus.carelink.report.application.ValueAddedServiceDispatchService.ManagedRequest;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

/**
 * A request as the manager's Extra services screen shows it: the request, plus where its work
 * order's visit stands and who is on it. Names are left to the client, which already holds the
 * elder and caregiver lists.
 */
public record ManagedValueAddedServiceRequestResponse(
        Long id,
        Long elderId,
        Long valueAddedServiceId,
        String serviceName,
        LocalDateTime requestedSchedule,
        String specialInstructions,
        ValueAddedServiceRequest.Status status,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        Long visitId,
        String visitStatus,
        Long caregiverId,
        boolean needsCaregiver) {

    public static ManagedValueAddedServiceRequestResponse from(ManagedRequest managed) {
        ValueAddedServiceRequest request = managed.request();
        return new ManagedValueAddedServiceRequestResponse(
                request.id(), request.elderId(), request.valueAddedServiceId(), managed.serviceName(),
                request.requestedSchedule(), request.specialInstructions(), request.status(),
                request.decidedAt(), request.createdAt(), request.visitId(), managed.visitStatus(),
                managed.caregiverId(), managed.needsCaregiver());
    }
}
