package sg.nus.carelink.report.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.report.application.ValueAddedServiceDispatchService;
import sg.nus.carelink.report.application.ValueAddedServiceRequestService;
import sg.nus.carelink.report.controller.dto.FamilyValueAddedServiceRequestCreate;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceDecisionRequest;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceRequestCreate;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceRequestResponse;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceResponse;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

/** REST endpoints for UC-EL02 and UC-FM08. */
@RestController
@RequestMapping("/api")
public class ValueAddedServiceController {
    private final IdentityService identity;
    private final ValueAddedServiceRequestService service;
    private final ValueAddedServiceDispatchService dispatch;

    public ValueAddedServiceController(IdentityService identity, ValueAddedServiceRequestService service,
            ValueAddedServiceDispatchService dispatch) {
        this.identity = identity;
        this.service = service;
        this.dispatch = dispatch;
    }

    @GetMapping("/elders/me/value-added-services")
    @PreAuthorize("hasRole('ELDER')")
    public List<ValueAddedServiceResponse> catalogue() {
        return service.availableServices().stream().map(ValueAddedServiceResponse::from).toList();
    }

    @GetMapping("/elders/me/value-added-service-requests")
    @PreAuthorize("hasRole('ELDER')")
    public List<ValueAddedServiceRequestResponse> elderRequests(Principal principal) {
        Long userId = identity.require(principal.getName()).id();
        return responses(service.listForElderUser(userId));
    }

    @PostMapping("/elders/me/value-added-service-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ELDER')")
    public ValueAddedServiceRequestResponse create(
            @Valid @RequestBody ValueAddedServiceRequestCreate request,
            Principal principal) {
        Long userId = identity.require(principal.getName()).id();
        ValueAddedServiceRequest saved = service.requestForElderUser(
                userId, request.valueAddedServiceId(), request.requestedSchedule(), request.specialInstructions());
        return response(saved);
    }

    /** The elder withdraws their request; its visit, if booked and not started, is called off. */
    @PostMapping("/elders/me/value-added-service-requests/{id}/cancellation")
    @PreAuthorize("hasRole('ELDER')")
    public ValueAddedServiceRequestResponse withdraw(@PathVariable Long id, Principal principal) {
        Long userId = identity.require(principal.getName()).id();
        return response(dispatch.cancelForElderUser(userId, id));
    }

    @GetMapping("/family/value-added-services")
    @PreAuthorize("hasRole('FAMILY')")
    public List<ValueAddedServiceResponse> familyCatalogue() {
        return catalogue();
    }

    /** A family member asks on the elder's behalf; their asking is their approval. */
    @PostMapping("/family/value-added-service-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FAMILY')")
    public ValueAddedServiceRequestResponse familyCreate(
            @Valid @RequestBody FamilyValueAddedServiceRequestCreate request,
            Principal principal) {
        return response(service.requestForFamily(principal.getName(), request.elderId(),
                request.valueAddedServiceId(), request.requestedSchedule(), request.specialInstructions()));
    }

    @GetMapping("/family/value-added-service-requests")
    @PreAuthorize("hasRole('FAMILY')")
    public List<ValueAddedServiceRequestResponse> familyRequests(
            @RequestParam Long elderId,
            Principal principal) {
        return responses(service.listForFamily(principal.getName(), elderId));
    }

    @PostMapping("/family/value-added-service-requests/{id}/decision")
    @PreAuthorize("hasRole('FAMILY')")
    public ValueAddedServiceRequestResponse decide(
            @PathVariable Long id,
            @Valid @RequestBody ValueAddedServiceDecisionRequest request,
            Principal principal) {
        return response(service.decideForFamily(principal.getName(), id, request.decision()));
    }

    private List<ValueAddedServiceRequestResponse> responses(List<ValueAddedServiceRequest> requests) {
        return requests.stream().map(this::response).toList();
    }

    private ValueAddedServiceRequestResponse response(ValueAddedServiceRequest request) {
        return ValueAddedServiceRequestResponse.from(request, service.requireService(request.valueAddedServiceId()));
    }
}
