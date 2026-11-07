package sg.nus.carelink.report.controller.dto;

import sg.nus.carelink.report.application.ValueAddedServiceCatalogueService;
import sg.nus.carelink.report.domain.model.ValueAddedService;

/** A service as the manager's catalogue shows it: with how often it was asked for, and so whether it may go. */
public record CatalogueServiceResponse(
        Long id,
        String name,
        String description,
        int durationMinutes,
        ValueAddedService.Status status,
        long requestCount,
        boolean removable) {
    public static CatalogueServiceResponse from(ValueAddedServiceCatalogueService.Entry entry) {
        ValueAddedService service = entry.service();
        return new CatalogueServiceResponse(service.id(), service.name(), service.description(),
                service.durationMinutes(), service.status(), entry.requests(), entry.removable());
    }
}
