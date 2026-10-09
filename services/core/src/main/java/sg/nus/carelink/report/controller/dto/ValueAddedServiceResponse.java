package sg.nus.carelink.report.controller.dto;

import sg.nus.carelink.report.domain.model.ValueAddedService;

public record ValueAddedServiceResponse(
        Long id,
        String name,
        String description,
        int durationMinutes,
        ValueAddedService.Status status) {
    public static ValueAddedServiceResponse from(ValueAddedService service) {
        return new ValueAddedServiceResponse(service.id(), service.name(), service.description(),
                service.durationMinutes(), service.status());
    }
}
