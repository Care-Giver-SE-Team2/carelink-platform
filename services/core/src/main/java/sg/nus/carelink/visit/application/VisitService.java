package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.ElderConfirmationRepository;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

/**
 * Application layer of the visit module
 * (the visit lifecycle: assignment, state transitions, tasks, vitals,
 * evidence and elder confirmation).
 *
 * <p>For UC-EL01, a completed visit can be confirmed or disputed by the elder.
 * The elder's response is stored in elder_confirmation. A dispute additionally
 * opens an incident linked to the same visit.
 *
 * <p>Business rules remain in the domain model wherever possible. This service
 * coordinates repositories and other application services.
 */
@Service
@Transactional
public class VisitService {

    private final VisitRepository visits;
    private final ElderConfirmationRepository confirmations;
    private final IncidentService incidents;
    private final Clock clock;

    public VisitService(
            VisitRepository visits,
            ElderConfirmationRepository confirmations,
            IncidentService incidents,
            Clock clock) {

        this.visits = visits;
        this.confirmations = confirmations;
        this.incidents = incidents;
        this.clock = clock;
    }

    /**
     * Existing generic visit lookup.
     */
    @Transactional(readOnly = true)
    public Optional<Visit> findVisit(
            Long id) {

        return visits.findById(
                id
        );
    }

    /**
     * The manager's day roster: every visit scheduled on {@code date}, assigned or not,
     * earliest first. Cancelled visits are left out; they no longer need anyone.
     *
     * @param date the day to list; null means today in Singapore
     */
    @Transactional(readOnly = true)
    public List<Visit> findDayRoster(LocalDate date) {
        LocalDate day = date != null
                ? date
                : LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));

        return visits.findScheduledBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream()
                .filter(visit -> visit.status() != Visit.Status.CANCELLED)
                .toList();
    }

    /**
     * UC-EL01:
     * Returns completed visits belonging to the elder that have not yet
     * received an elder confirmation.
     *
     * <p>A visit remains COMPLETED while waiting for the elder's answer.
     * Whether it still requires an answer is determined by the absence of
     * elder_confirmation for that visit.
     */
    @Transactional(readOnly = true)
    public List<Visit> findAwaitingConfirmation(
            Long elderId) {

        return visits
                .findCompletedByElderId(
                        elderId
                )
                .stream()
                .filter(visit ->
                        !confirmations
                                .existsByVisitId(
                                        visit.id()
                                )
                )
                .toList();
    }

    /**
     * UC-EL01:
     * Records the elder's answer for one completed visit.
     *
     * <p>CONFIRMED stores the elder confirmation and feedback.
     *
     * <p>DISPUTED stores the elder confirmation and feedback, then creates
     * a SERVICE incident linked to the visit so the existing manager
     * exception workflow can investigate it.
     */
    public ElderConfirmation submitElderConfirmation(
            Long elderId,
            Long reportedByUserId,
            Long visitId,
            ElderConfirmation.ConfirmationStatus status,
            Byte rating,
            String comment) {

        Visit visit =
                visits.findById(
                        visitId
                )
                        .orElseThrow(() ->
                                new ResourceNotFound(
                                        "Visit",
                                        visitId
                                )
                        );

        /*
         * An elder must not be able to inspect or confirm another elder's
         * visit through this endpoint.
         *
         * Return not-found rather than access-denied so the existence of
         * another elder's visit is not disclosed.
         */
        if (!elderId.equals(
                visit.elderId())) {

            throw new ResourceNotFound(
                    "Visit",
                    visitId
            );
        }

        /*
         * EL01 only applies once the caregiver has completed the visit.
         */
        if (visit.status()
                != Visit.Status.COMPLETED) {

            throw new BusinessRuleViolation(
                    "VISIT_NOT_AWAITING_ELDER_CONFIRMATION",
                    "Only a completed visit can be confirmed by the elder."
            );
        }

        /*
         * visit_id is unique in elder_confirmation at database level.
         *
         * Check explicitly as well so the caller receives a meaningful
         * business conflict instead of a raw database constraint failure.
         */
        if (confirmations
                .existsByVisitId(
                        visitId
                )) {

            throw new BusinessRuleViolation(
                    "VISIT_ALREADY_CONFIRMED",
                    "This visit has already received an elder confirmation."
            );
        }

        ElderConfirmation confirmation =
                ElderConfirmation.submit(
                        visitId,
                        elderId,
                        status,
                        rating,
                        comment,
                        LocalDateTime.now(
                                clock
                        )
                );

        ElderConfirmation saved =
                confirmations.save(
                        confirmation
                );

        /*
         * A dispute is operationally different from ordinary feedback.
         * The care team must investigate it, so it enters the existing
         * incident workflow.
         */
        if (status
                == ElderConfirmation
                        .ConfirmationStatus
                        .DISPUTED) {

            String description =
                    saved.comment() == null
                            ? "Elder disputed completion of the visit."
                            : saved.comment();

            incidents
                    .createElderServiceDispute(
                            elderId,
                            visitId,
                            reportedByUserId,
                            description
                    );
        }

        return saved;
    }
}