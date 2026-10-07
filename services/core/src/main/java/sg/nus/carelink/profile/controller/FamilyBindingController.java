package sg.nus.carelink.profile.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.FamilyBindingService;
import sg.nus.carelink.profile.controller.dto.FamilyBindingCreateRequest;
import sg.nus.carelink.profile.controller.dto.FamilyBindingResponse;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.FamilyMember;

/**
 * Elder-side endpoints for EL04 family binding management.
 */
@RestController
@RequestMapping("/api/elders/me/family-bindings")
@PreAuthorize("hasRole('ELDER')")
public class FamilyBindingController {

    private final IdentityService identityService;
    private final FamilyBindingService service;

    public FamilyBindingController(
            IdentityService identityService,
            FamilyBindingService service) {

        this.identityService =
                identityService;

        this.service =
                service;
    }

    /**
     * Lists the current elder's family bindings.
     */
    @GetMapping
    public List<FamilyBindingResponse> list(
            Principal principal) {

        AppUser currentUser =
                identityService.require(
                        principal.getName()
                );

        return service
                .listForElderUser(
                        currentUser.id()
                )
                .stream()
                .map(this::response)
                .toList();
    }

    /**
     * Creates a family binding request for the current elder.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyBindingResponse create(
            @Valid
            @RequestBody
            FamilyBindingCreateRequest request,
            Principal principal) {

        AppUser currentUser =
                identityService.require(
                        principal.getName()
                );

        ElderFamilyBinding binding =
                service.create(
                        currentUser.id(),
                        request.familyUsername(),
                        request.relationship(),
                        request.primaryContact(),
                        request.accessScope()
                );

        return response(binding);
    }

    /**
     * Revokes one of the current elder's family bindings.
     */
    @DeleteMapping("/{bindingId}")
    public FamilyBindingResponse revoke(
            @PathVariable Long bindingId,
            Principal principal) {

        AppUser currentUser =
                identityService.require(
                        principal.getName()
                );

        ElderFamilyBinding binding =
                service.revoke(
                        currentUser.id(),
                        bindingId
                );

        return response(binding);
    }

    private FamilyBindingResponse response(
            ElderFamilyBinding binding) {

        FamilyMember familyMember =
                service.requireFamilyMember(
                        binding.familyMemberId()
                );

        return FamilyBindingResponse.from(
                binding,
                familyMember
        );
    }
}