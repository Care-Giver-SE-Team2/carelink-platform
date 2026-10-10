package sg.nus.carelink.visit.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.visit.application.VisitService;
import sg.nus.carelink.visit.controller.dto.ElderVisitConfirmationRequest;
import sg.nus.carelink.visit.controller.dto.ElderVisitConfirmationResponse;
import sg.nus.carelink.visit.controller.dto.PendingElderVisitResponse;
import sg.nus.carelink.visit.domain.model.ElderConfirmation;

/**
 * Elder-facing HTTP API for UC-EL01.
 */
@RestController
@RequestMapping("/api/elders/me/visits")
@PreAuthorize("hasRole('ELDER')")
public class ElderVisitConfirmationController {

    private final IdentityService identityService;
    private final ProfileService profileService;
    private final VisitService visitService;

    public ElderVisitConfirmationController(
            IdentityService identityService,
            ProfileService profileService,
            VisitService visitService) {

        this.identityService =
                identityService;

        this.profileService =
                profileService;

        this.visitService =
                visitService;
    }

    /**
     * Lists completed visits that still require this elder's answer.
     */
    @GetMapping("/awaiting-confirmation")
    public List<PendingElderVisitResponse>
            awaitingConfirmation(
                    Principal principal) {

        Elder elder =
                currentElder(principal);

        return visitService
                .findAwaitingConfirmation(
                        elder.id()
                )
                .stream()
                .map(
                        PendingElderVisitResponse::from
                )
                .toList();
    }

    /**
     * Submits EL01 for one visit.
     */
    @PostMapping("/{visitId}/confirmation")
    @ResponseStatus(HttpStatus.CREATED)
    public ElderVisitConfirmationResponse confirm(
            @PathVariable Long visitId,
            @Valid
            @RequestBody
            ElderVisitConfirmationRequest request,
            Principal principal) {

        AppUser user =
                identityService.require(
                        principal.getName()
                );

        Elder elder =
                profileService
                        .requireElderByUserId(
                                user.id()
                        );

        ElderConfirmation saved =
                visitService
                        .submitElderConfirmation(
                                elder.id(),
                                user.id(),
                                visitId,
                                request.confirmationStatus(),
                                request.rating(),
                                request.comment()
                        );

        return ElderVisitConfirmationResponse
                .from(saved);
    }

    private Elder currentElder(
            Principal principal) {

        AppUser user =
                identityService.require(
                        principal.getName()
                );

        return profileService
                .requireElderByUserId(
                        user.id()
                );
    }
}