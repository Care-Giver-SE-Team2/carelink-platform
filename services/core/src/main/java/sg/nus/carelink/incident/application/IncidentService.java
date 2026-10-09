package sg.nus.carelink.incident.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.incident.domain.model.ContactAttempt;
import sg.nus.carelink.incident.domain.model.EscalationChain;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.model.PageSlice;
import sg.nus.carelink.incident.domain.model.Playbook;
import sg.nus.carelink.incident.domain.repository.IncidentLogRepository;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Application layer of the incident module: one public method per step of UC-MG05, plus
 * the entry points that raise an incident in the first place
 * (UC-EL03, UC-EL01, UC-CG04 and UC-SYS03).
 *
 * <p>Each method does the same four things and nothing else: load through the ports, call
 * the domain model, save, write the timeline. The rules themselves are in
 * {@code domain.model}; the routing decisions are in {@code domain.service} behind
 * {@link EscalationService}. If a rule appears in this file, it is in the wrong place.
 *
 * <p>Nothing here catches a {@link BusinessRuleViolation}: an illegal transition is meant
 * to reach the client as HTTP 409, and swallowing it would let a rejected take-over look
 * like a successful one.
 */
@Service
@Transactional
public class IncidentService {

    /**
     * What the manager's queue shows when nobody has asked for a particular status: every
     * incident that is not finished with. Stated here rather than in the repository because
     * "still needs attention" is a decision about the use case, not about storage.
     */
    private static final Set<Incident.Status>
            STILL_NEEDS_ATTENTION =
            Set.of(
                    Incident.Status.OPEN,
                    Incident.Status.ACKNOWLEDGED,
                    Incident.Status.IN_PROGRESS,
                    Incident.Status.UNRESOLVED_ESCALATED
            );

    /** A page big enough for a shift's worth of incidents and small enough to be one read. */
    private static final int MAX_PAGE_SIZE =
            100;

    private final IncidentRepository incidents;
    private final IncidentLogRepository timeline;
    private final EscalationService escalation;
    private final Clock clock;

    IncidentService(
            IncidentRepository incidents,
            IncidentLogRepository timeline,
            EscalationService escalation,
            Clock clock) {

        this.incidents = incidents;
        this.timeline = timeline;
        this.escalation = escalation;
        this.clock = clock;
    }

    // ------------------------------------------------------------------- raising ---

    /**
     * UC-SYS03: join the locked Visit transition and trigger ledger transaction.
     * Routing registers the family event; do not publish or notify the family a second time.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Incident raiseForMissedCheckIn(Long elderId, Long visitId,
            LocalDateTime dueAt, LocalDateTime observedAt) {
        var saved = incidents.save(Incident.raisedForMissedCheckIn(elderId, visitId, dueAt, observedAt));
        timeline.save(IncidentLog.systemEntry(saved.id(), IncidentLog.Action.REPORTED,
                "assigned caregiver has not checked in after the allowed lateness threshold", observedAt));
        return escalation.routeNewIncident(saved);
    }

    /**
     * UC-EL03: an elder triggers the one-tap emergency call.
     *
     * <p>Recording the call is the elder module's, written there and kept as it was. What
     * follows is UC-MG05's trigger and step 1, the same as for every other incident: the
     * call goes on its timeline and is routed - everyone who could act is told at once, a
     * named manager is made responsible and the countdown starts, so an SOS can never sit
     * in the table waiting for somebody to notice it.
     */
    public Incident createElderEmergency(
            Long elderId,
            Long reportedByUserId,
            BigDecimal latitude,
            BigDecimal longitude,
            String locationText,
            String description) {

        Incident incident =
                Incident.createElderSos(
                        elderId,
                        reportedByUserId,
                        latitude,
                        longitude,
                        locationText,
                        description
                );

        Incident saved = incidents.save(
                incident
        );

        timeline.save(
                IncidentLog.entry(
                        saved.id(),
                        actorLabel(
                                reportedByUserId,
                                "elder"
                        ),
                        IncidentLog.Action.REPORTED,
                        "emergency call by the elder",
                        now()
                )
        );

        return escalation.routeNewIncident(
                saved
        );
    }

    /**
     * UC-EL01:
     * Opens an operational incident when an elder disputes a completed visit.
     *
     * <p>Unlike EL03, this incident is associated with a visit and is routed immediately
     * through the existing escalation workflow. The manager therefore sees the dispute
     * through the same exception queue used by UC-MG05.
     */
    public Incident createElderServiceDispute(
            Long elderId,
            Long visitId,
            Long reportedByUserId,
            String description) {

        Incident saved =
                incidents.save(
                        Incident
                                .reportedByElderServiceDispute(
                                        elderId,
                                        visitId,
                                        reportedByUserId,
                                        description,
                                        now()
                                )
                );

        timeline.save(
                IncidentLog.entry(
                        saved.id(),
                        actorLabel(
                                reportedByUserId,
                                "elder"
                        ),
                        IncidentLog.Action.REPORTED,
                        "service disputed by elder",
                        now()
                )
        );

        return escalation.routeNewIncident(
                saved
        );
    }

    /** UC-CG04: a caregiver reports a care exception during or after a visit. */
    public Incident reportByCaregiver(
            Long elderId,
            Long visitId,
            Long reportedByUserId,
            Incident.Category category,
            Incident.Severity severity,
            String description) {

        Incident saved =
                incidents.save(
                        Incident.reportedByCaregiver(
                                elderId,
                                visitId,
                                reportedByUserId,
                                category,
                                severity,
                                description,
                                now()
                        )
                );

        timeline.save(
                IncidentLog.entry(
                        saved.id(),
                        actorLabel(
                                reportedByUserId,
                                "caregiver"
                        ),
                        IncidentLog.Action.REPORTED,
                        "reported by caregiver",
                        now()
                )
        );

        return escalation.routeNewIncident(
                saved
        );
    }

    /**
     * UC-MG03: a visit reached its start time with nobody assigned. Raised by the roster's
     * scan, not by a person, and routed like any other incident so it lands in the
     * manager's queue with a responder and a countdown.
     */
    public Incident raiseForUncoveredVisit(
            Long elderId,
            Long visitId,
            String description) {

        Incident saved =
                incidents.save(
                        Incident.raisedForUncoveredVisit(
                                elderId,
                                visitId,
                                description,
                                now()
                        )
                );

        timeline.save(
                IncidentLog.entry(
                        saved.id(),
                        "system",
                        IncidentLog.Action.REPORTED,
                        "visit started with no caregiver assigned",
                        now()
                )
        );

        return escalation.routeNewIncident(
                saved
        );
    }

    /**
     * UC-MG04 exception 3a: an absence vacated a visit and nobody is free to take it. Raised by
     * the re-rostering, before the visit is due, and routed like any other incident: the gap
     * gets a named responder and a countdown, and cannot fade out of sight when a timer runs
     * out ("待办不因超时而消失").
     *
     * <p>It is the same kind of incident as {@link #raiseForUncoveredVisit} - a visit nobody
     * will check in to - and only the timeline says how it came about.
     */
    public Incident raiseForUnfilledAbsence(
            Long elderId,
            Long visitId,
            String description) {

        Incident saved =
                incidents.save(
                        Incident.raisedForUncoveredVisit(
                                elderId,
                                visitId,
                                description,
                                now()
                        )
                );

        timeline.save(
                IncidentLog.entry(
                        saved.id(),
                        "system",
                        IncidentLog.Action.REPORTED,
                        "caregiver absent and nobody free to replace them",
                        now()
                )
        );

        return escalation.routeNewIncident(
                saved
        );
    }

    /**
     * UC-MG03 / UC-MG04: a visit falls on a caregiver's approved leave, is due soon, and is
     * still theirs because nobody has re-rostered it. Raised by the roster's reminder scan
     * before the visit, so the gap reaches the manager's queue while there is time to act.
     *
     * <p>The same kind of incident as {@link #raiseForUncoveredVisit} - a visit nobody will
     * check in to unless somebody acts - and only the timeline says how it came about.
     */
    public Incident raiseForUnrosteredLeaveVisit(
            Long elderId,
            Long visitId,
            String description) {

        Incident saved =
                incidents.save(
                        Incident.raisedForUncoveredVisit(
                                elderId,
                                visitId,
                                description,
                                now()
                        )
                );

        timeline.save(
                IncidentLog.entry(
                        saved.id(),
                        "system",
                        IncidentLog.Action.REPORTED,
                        "visit due soon is still with a caregiver on leave",
                        now()
                )
        );

        return escalation.routeNewIncident(
                saved
        );
    }

    /**
     * UC-MG08 exception 4a: a manager went to watch a visit and the caregiver never came. The
     * spot check turns into a missed-visit exception and takes the UC-MG05 route, like any
     * other visit nobody checked in to; the manager who saw it is on the timeline.
     */
    public Incident raiseForMissedSpotCheck(
            Long elderId,
            Long visitId,
            String description,
            String actor) {

        Incident saved =
                incidents.save(
                        Incident.raisedForUncoveredVisit(
                                elderId,
                                visitId,
                                description,
                                now()
                        )
                );

        timeline.save(
                IncidentLog.entry(
                        saved.id(),
                        actor,
                        IncidentLog.Action.REPORTED,
                        "caregiver did not turn up for a spot-checked visit",
                        now()
                )
        );

        return escalation.routeNewIncident(
                saved
        );
    }

    // ------------------------------------------------------------------ handling ---

    /**
     * UC-MG05 step 3: a manager takes the incident over. The countdown stops, which is what
     * takes it out of the scheduled scan's reach.
     *
     * <p>A take-over that is refused because somebody else got there first is written to the
     * timeline before the rejection is thrown, so the attempt leaves a trace. The refusal must
     * not roll that row back with it, hence {@code noRollbackFor}: when the refusal is thrown,
     * the timeline entry is the only thing this transaction has written.
     */
    @Transactional(noRollbackFor = BusinessRuleViolation.class)
    public Incident claim(
            Long incidentId,
            Long userId,
            String actor) {

        Incident incident =
                require(incidentId);

        try {
            Incident claimed =
                    incidents.save(
                            incident.claimBy(
                                    userId
                            )
                    );

            timeline.save(
                    IncidentLog.entry(
                            incidentId,
                            actor,
                            IncidentLog.Action.CLAIMED,
                            "taken over; countdown stopped",
                            now()
                    )
            );

            return claimed;
        } catch (
                BusinessRuleViolation rejected
        ) {
            timeline.save(
                    IncidentLog.entry(
                            incidentId,
                            actor,
                            IncidentLog.Action.CLAIM_REJECTED,
                            rejected.getMessage(),
                            now()
                    )
            );

            throw rejected;
        }
    }

    /** UC-MG05 exception 3a and UC-SYS02: hand the incident to the next level. */
    public Incident escalate(
            Long incidentId,
            String reason,
            String actor) {

        Incident incident =
                require(incidentId);

        if (!incident.awaitingTakeOver()) {
            throw new BusinessRuleViolation(
                    "INCIDENT_NOT_AWAITING_TAKE_OVER",
                    "Only an incident still waiting to be taken over can be escalated; it is "
                            + incident.status()
            );
        }

        return escalation.escalate(
                incident,
                reason == null
                        || reason.isBlank()
                        ? "escalated by hand"
                        : reason,
                actor
        );
    }

    /**
     * UC-MG05 step 4: record an attempt to reach the family, reached or not.
     *
     * <p>When the family could not be reached the caller is told which playbook applies, so
     * the manager can act without waiting — "家属联络不上：启用标准处置预案先行处置".
     */
    public ContactOutcome recordContactAttempt(
            Long incidentId,
            ContactAttempt attempt,
            String actor) {

        Incident incident =
                require(incidentId);

        timeline.save(
                IncidentLog.entry(
                        incidentId,
                        actor,
                        IncidentLog.Action.CONTACT_ATTEMPTED,
                        attempt.describe(),
                        now()
                )
        );

        Optional<Playbook> fallback =
                attempt.reachedTheFamily()
                        ? Optional.empty()
                        : Playbook.forCategory(
                                incident.category()
                        );

        return new ContactOutcome(
                incident,
                attempt,
                fallback.orElse(null)
        );
    }

    /** UC-MG05 step 5: apply the standard response for this category. */
    public Incident applyPlaybook(
            Long incidentId,
            String playbookCode,
            String actor) {

        Incident incident =
                require(incidentId);

        Playbook playbook =
                Playbook.byCode(
                        playbookCode
                )
                        .orElseThrow(() ->
                                new ResourceNotFound(
                                        "Playbook",
                                        playbookCode
                                )
                        );

        if (playbook.category()
                != incident.category()) {

            throw new BusinessRuleViolation(
                    "PLAYBOOK_CATEGORY_MISMATCH",
                    "Playbook %s is for %s incidents, this one is %s"
                            .formatted(
                                    playbook.code(),
                                    playbook.category(),
                                    incident.category()
                            )
            );
        }

        timeline.save(
                IncidentLog.entry(
                        incidentId,
                        actor,
                        IncidentLog.Action.PLAYBOOK_APPLIED,
                        "%s - %s".formatted(
                                playbook.code(),
                                playbook.title()
                        ),
                        now()
                )
        );

        return incident;
    }

    /**
     * UC-MG05 alternative 5a: the situation worsened. The severity changes, the chain is
     * rebuilt for it, and the same timeline continues.
     */
    public Incident changeSeverity(
            Long incidentId,
            Incident.Severity severity,
            String reason,
            String actor) {

        Incident incident =
                require(incidentId);

        Incident.Severity before =
                incident.severity();

        Incident changed =
                incidents.save(
                        incident.changeSeverityTo(
                                severity
                        )
                );

        timeline.save(
                IncidentLog.entry(
                        incidentId,
                        actor,
                        IncidentLog.Action.SEVERITY_CHANGED,
                        "%s -> %s: %s".formatted(
                                before,
                                severity,
                                reason == null
                                        ? "no reason given"
                                        : reason
                        ),
                        now()
                )
        );

        return escalation
                .reassembleAfterSeverityChange(
                        changed
                );
    }

    /**
     * UC-MG05 step 6: record the conclusion and close the incident.
     *
     * <p>Only the responder who took it over may close it. Anyone else gets 403 rather than
     * 409, because the request is well formed and the incident is in a closable state — the
     * caller is simply not the person handling it.
     */
    public Incident resolve(
            Long incidentId,
            Long userId,
            String resolutionNote,
            String outcome,
            String actor) {

        Incident incident =
                require(incidentId);

        if (!incident.isHandledBy(
                userId)) {

            throw new AccessDeniedException(
                    "Only the responder handling this incident may resolve it"
            );
        }

        if (resolutionNote == null
                || resolutionNote.isBlank()) {

            throw new BusinessRuleViolation(
                    "RESOLUTION_NOTE_REQUIRED",
                    "An incident cannot be closed without a resolution note"
            );
        }

        Incident resolved =
                incidents.save(
                        incident.resolveAt(
                                now()
                        )
                );

        timeline.save(
                IncidentLog.entry(
                        incidentId,
                        actor,
                        IncidentLog.Action.RESOLVED,
                        "%s :: %s".formatted(
                                outcome == null
                                        ? "HANDLED_ON_SITE"
                                        : outcome,
                                resolutionNote
                        ),
                        now()
                )
        );

        return resolved;
    }

    // -------------------------------------------------------------------- reading ---

    @Transactional(readOnly = true)
    public Optional<Incident> findIncident(
            Long id) {

        return incidents.findById(
                id
        );
    }

    @Transactional(readOnly = true)
    public List<IncidentLog> timelineOf(
            Long incidentId) {

        require(incidentId);

        return timeline.findTimeline(
                incidentId
        );
    }

    @Transactional(readOnly = true)
    public EscalationChain escalationChainOf(
            Long incidentId) {

        return escalation.describeChain(
                require(incidentId)
        );
    }

    @Transactional(readOnly = true)
    public List<Incident> forElder(
            Long elderId) {

        return incidents.findByElder(
                elderId
        );
    }

    /**
     * UC-MG05 step 2: the queue the manager's console opens on.
     *
     * <p>Without a status filter it shows everything that still owes somebody an answer,
     * including UNRESOLVED_ESCALATED — an incident the chain ran out on is the one thing a
     * manager must not have to go looking for ("链用尽标未解决－已升级并置顶").
     * Asking for one status shows exactly that status, closed incidents included, because
     * the same endpoint is how anyone reviews what happened afterwards.
     *
     * <p>Page and size are clamped rather than validated. They come from a URL, where a
     * hand-typed {@code page=-1} is a slip and not an attack; answering with the first page
     * is more useful than a 400, and a size nobody bounded is how one request reads the
     * whole table.
     */
    @Transactional(readOnly = true)
    public PageSlice<Incident> queue(
            Incident.Status status,
            Incident.Severity severity,
            Long elderId,
            int page,
            int size) {

        Set<Incident.Status> statuses =
                status == null
                        ? STILL_NEEDS_ATTENTION
                        : Set.of(status);

        return incidents.findQueue(
                statuses,
                severity,
                elderId,
                Math.max(
                        page,
                        0
                ),
                Math.min(
                        Math.max(
                                size,
                                1
                        ),
                        MAX_PAGE_SIZE
                )
        );
    }

    @Transactional(readOnly = true)
    public List<Playbook> playbooks() {
        return List.of(
                Playbook.values()
        );
    }

    // -------------------------------------------------------------------- helpers ---

    private Incident require(Long id) {
        return incidents
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFound(
                                "Incident",
                                id
                        )
                );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(
                clock
        );
    }

    private static String actorLabel(
            Long userId,
            String role) {

        return userId == null
                ? role
                : "%s:%d".formatted(
                        role,
                        userId
                );
    }

    /**
     * What came back from recording a contact attempt: the incident, what was tried, and the
     * playbook to fall back on when the family could not be reached.
     */
    public record ContactOutcome(
            Incident incident,
            ContactAttempt attempt,
            Playbook suggestedPlaybook) {

        public boolean hasFallback() {
            return suggestedPlaybook
                    != null;
        }
    }
}
