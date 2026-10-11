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

import sg.nus.carelink.visit.application.Accounts;
import sg.nus.carelink.visit.application.Elders;
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

    private final Accounts accounts;
    private final Elders elders;
    private final VisitService visitService;

    public ElderVisitConfirmationController(
            Accounts accounts,
            Elders elders,
            VisitService visitService) {

        this.accounts =
                accounts;

        this.elders =
                elders;

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

        Long elderId =
                currentElderId(principal);

        return visitService
                .findAwaitingConfirmation(
                        elderId
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

        Long userId =
                accounts.idOf(
                        principal.getName()
                );

        Long elderId =
                elders.requireElderIdOfUser(
                        userId
                );

        ElderConfirmation saved =
                visitService
                        .submitElderConfirmation(
                                elderId,
                                userId,
                                visitId,
                                request.confirmationStatus(),
                                request.rating(),
                                request.comment()
                        );

        return ElderVisitConfirmationResponse
                .from(saved);
    }

    private Long currentElderId(
            Principal principal) {

        return elders.requireElderIdOfUser(
                accounts.idOf(
                        principal.getName()
                )
        );
    }
}