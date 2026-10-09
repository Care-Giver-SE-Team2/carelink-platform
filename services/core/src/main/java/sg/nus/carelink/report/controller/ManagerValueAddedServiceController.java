package sg.nus.carelink.report.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sg.nus.carelink.report.application.ValueAddedServiceDispatchService;
import sg.nus.carelink.report.controller.dto.CaregiverAssignmentRequest;
import sg.nus.carelink.report.controller.dto.CaregiverOptionResponse;
import sg.nus.carelink.report.controller.dto.ManagedValueAddedServiceRequestResponse;

/** The manager's Extra services screen: every request, staffing a dispatched visit, and cancelling. */
@RestController
@RequestMapping("/api/value-added-service-requests")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerValueAddedServiceController {
    private final ValueAddedServiceDispatchService service;

    public ManagerValueAddedServiceController(ValueAddedServiceDispatchService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManagedValueAddedServiceRequestResponse> list() {
        return service.listForManager().stream().map(ManagedValueAddedServiceRequestResponse::from).toList();
    }

    @GetMapping("/{id}/caregiver-options")
    public List<CaregiverOptionResponse> caregiverOptions(@PathVariable Long id) {
        return service.caregiverOptions(id).stream().map(CaregiverOptionResponse::from).toList();
    }

    @PostMapping("/{id}/caregiver")
    public ManagedValueAddedServiceRequestResponse assignCaregiver(
            @PathVariable Long id,
            @Valid @RequestBody CaregiverAssignmentRequest request,
            Principal principal) {
        return ManagedValueAddedServiceRequestResponse.from(
                service.assignCaregiver(id, request.caregiverId(), principal.getName()));
    }

    @PostMapping("/{id}/cancellation")
    public ManagedValueAddedServiceRequestResponse cancel(@PathVariable Long id, Principal principal) {
        return ManagedValueAddedServiceRequestResponse.from(service.cancel(id, principal.getName()));
    }
}
