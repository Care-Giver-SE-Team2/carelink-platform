package sg.nus.carelink.report.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sg.nus.carelink.report.application.ValueAddedServiceCatalogueService;
import sg.nus.carelink.report.controller.dto.CatalogueServiceResponse;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceCreate;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceResponse;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceUpdate;

/**
 * The Catalogue view of the manager's Extra services screen (D21): every service, withdrawn ones too, adding
 * one, changing one, and removing one nobody ever asked for. There is no rename; withdrawing a service that was
 * asked for is how it goes.
 */
@RestController
@RequestMapping("/api/value-added-services")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerValueAddedServiceCatalogueController {
    private final ValueAddedServiceCatalogueService catalogue;

    public ManagerValueAddedServiceCatalogueController(ValueAddedServiceCatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping
    public List<CatalogueServiceResponse> list() {
        return catalogue.all().stream().map(CatalogueServiceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ValueAddedServiceResponse add(@Valid @RequestBody ValueAddedServiceCreate request) {
        return ValueAddedServiceResponse.from(
                catalogue.add(request.name(), request.description(), request.durationMinutes()));
    }

    @PutMapping("/{id}")
    public ValueAddedServiceResponse revise(@PathVariable Long id, @Valid @RequestBody ValueAddedServiceUpdate request) {
        return ValueAddedServiceResponse.from(
                catalogue.revise(id, request.description(), request.durationMinutes(), request.status()));
    }

    /** 204 once removed; 409 {@code VALUE_ADDED_SERVICE_IN_USE} when a request names it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        catalogue.remove(id);
    }
}
