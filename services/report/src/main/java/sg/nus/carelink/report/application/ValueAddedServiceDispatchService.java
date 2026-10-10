package sg.nus.carelink.report.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.report.application.ValueAddedNotifier.Why;
import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * An extra service once it has been asked for: the manager's overview, a caregiver for a
 * dispatched visit nobody holds, and calling a request off - by a manager or by the elder who
 * asked. Also keeps requests in step with time and with their visits, so the elder, family and
 * reports never read "booked" for a service that cannot happen: a request nobody answered lapses,
 * and one whose visit failed before anybody started on it is closed.
 *
 * <p>Visits are read and changed only through visit's and rostering's internal APIs, behind this
 * module's own ports.
 */
@Service
@Transactional
public class ValueAddedServiceDispatchService {

    /** Visit states in which the work order was carried out. */
    static final Set<String> CARRIED_OUT = Set.of("COMPLETED", "VERIFIED", "AUTO_CLOSED");

    /** How long before the requested time the family is reminded of a request still unanswered. */
    static final java.time.Duration REMIND_WITHIN = java.time.Duration.ofHours(24);

    static final String CANCEL_REASON = "The extra service was cancelled by a manager";
    static final String WITHDRAW_REASON = "The elder withdrew the extra-service request";

    private final ValueAddedServiceRequestRepository requests;
    private final ValueAddedServiceRepository services;
    private final StandaloneVisits standaloneVisits;
    private final VisitReassignment visits;
    private final VisitCover cover;
    private final Accounts accounts;
    private final Elders elders;
    private final ValueAddedNotifier notifier;
    private final Clock clock;

    @SuppressWarnings("java:S107") // one collaborator per module the request's life reaches
    public ValueAddedServiceDispatchService(
            ValueAddedServiceRequestRepository requests,
            ValueAddedServiceRepository services,
            StandaloneVisits standaloneVisits,
            VisitReassignment visits,
            VisitCover cover,
            Accounts accounts,
            Elders elders,
            ValueAddedNotifier notifier,
            Clock clock) {
        this.requests = requests;
        this.services = services;
        this.standaloneVisits = standaloneVisits;
        this.visits = visits;
        this.cover = cover;
        this.accounts = accounts;
        this.elders = elders;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Every request, newest first, each with its service's name and its visit as it stands. */
    @Transactional(readOnly = true)
    public List<ManagedRequest> listForManager() {
        Map<Long, String> names = services.findAll().stream()
                .collect(Collectors.toMap(ValueAddedService::id, ValueAddedService::name, (a, b) -> a));
        return requests.findAll().stream()
                .map(request -> managed(request, names.get(request.valueAddedServiceId())))
                .toList();
    }

    /** Who can take the dispatched visit, best first, then who cannot and why. */
    @Transactional(readOnly = true)
    public List<VisitCover.Option> caregiverOptions(Long requestId) {
        return cover.options(dispatchedVisitId(requireRequest(requestId)));
    }

    /** Puts a caregiver on a dispatched visit that has none, and tells them and the family. */
    public ManagedRequest assignCaregiver(Long requestId, Long caregiverId, String username) {
        ValueAddedServiceRequest request = requireRequest(requestId);
        cover.cover(dispatchedVisitId(request), caregiverId, accounts.idOf(username));
        String name = serviceName(request);
        notifier.caregiverAssigned(request, name, caregiverId);
        return managed(request, name);
    }

    /**
     * A manager calls the request off. A dispatched request's visit is called off with it, so it
     * leaves the caregiver's schedule; once somebody has started on it, it is too late.
     */
    public ManagedRequest cancel(Long requestId, String username) {
        ValueAddedServiceRequest request = requireRequest(requestId);
        return managed(cancel(request, accounts.idOf(username), CANCEL_REASON, Why.BY_MANAGER),
                serviceName(request));
    }

    /** The elder withdraws their own request, on the same terms as a manager's cancellation. */
    public ValueAddedServiceRequest cancelForElderUser(Long userId, Long requestId) {
        Long elderId = elders.requireElderIdOfUser(userId);
        ValueAddedServiceRequest request = requests.findById(requestId)
                .filter(found -> found.elderId().equals(elderId))
                .orElseThrow(() -> new ResourceNotFound("Value-added service request", requestId));
        return cancel(request, userId, WITHDRAW_REASON, Why.BY_ELDER);
    }

    private ValueAddedServiceRequest cancel(ValueAddedServiceRequest request, Long byUserId, String reason, Why why) {
        ValueAddedServiceRequest cancelled = request.cancelled();
        Optional<StandaloneVisits.State> visit = visit(request);
        if (visit.isPresent() && !"CANCELLED".equals(visit.get().status())) {
            if (!"SCHEDULED".equals(visit.get().status())) {
                throw new BusinessRuleViolation(
                        "VALUE_ADDED_VISIT_UNDER_WAY",
                        "The visit for this request is %s and can no longer be cancelled."
                                .formatted(visit.get().status()));
            }
            visits.callOff(request.visitId(), new VisitReassignment.Change(null, byUserId, null, reason));
        }
        ValueAddedServiceRequest saved = requests.save(cancelled);
        notifier.cancelled(saved, serviceName(request), visit.map(StandaloneVisits.State::caregiverId).orElse(null), why);
        return saved;
    }

    /**
     * Brings every dispatched request into line with its visit: carried out means completed;
     * called off (for an absence, say) means cancelled; and an exception before anybody checked
     * in - nobody was staffed in time, or the caregiver never arrived - means the service will not
     * happen on that visit, so the request is closed and the family told. An exception after
     * check-in is left for the incident: some of the service may have been given.
     *
     * @return how many requests changed
     */
    public int settleWithVisits() {
        int settled = 0;
        for (ValueAddedServiceRequest request : requests.findByStatus(ValueAddedServiceRequest.Status.DISPATCHED)) {
            Optional<StandaloneVisits.State> visit = visit(request);
            if (visit.isEmpty()) {
                continue;
            }
            String status = visit.get().status();
            if (CARRIED_OUT.contains(status)) {
                requests.save(request.completed());
                settled++;
            }
            else if ("CANCELLED".equals(status)) {
                requests.save(request.cancelled());
                settled++;
            }
            else if ("EXCEPTION".equals(status) && !visit.get().checkedIn()) {
                ValueAddedServiceRequest closed = requests.save(request.cancelled());
                notifier.cancelled(closed, serviceName(request), visit.get().caregiverId(), Why.NOT_PROVIDED);
                settled++;
            }
        }
        return settled;
    }

    /**
     * Follows up requests the family has not answered: a reminder once the requested time is
     * within a day, and the request lapses once it is too late to arrange.
     *
     * @return how many requests lapsed
     */
    public int followUpUnanswered() {
        LocalDateTime now = LocalDateTime.now(clock);
        int lapsed = 0;
        for (ValueAddedServiceRequest request : requests.findByStatus(ValueAddedServiceRequest.Status.PENDING_APPROVAL)) {
            if (request.requestedSchedule() == null) {
                continue;
            }
            if (!request.answerableAt(now)) {
                ValueAddedServiceRequest closed = requests.save(request.cancelled());
                notifier.cancelled(closed, serviceName(request), null, Why.NOT_ANSWERED);
                lapsed++;
            }
            else if (request.requestedSchedule().isBefore(now.plus(REMIND_WITHIN))) {
                notifier.reminder(request, serviceName(request));
            }
        }
        return lapsed;
    }

    private ManagedRequest managed(ValueAddedServiceRequest request, String serviceName) {
        Optional<StandaloneVisits.State> visit = visit(request);
        return new ManagedRequest(request, serviceName,
                visit.map(StandaloneVisits.State::status).orElse(null),
                visit.map(StandaloneVisits.State::caregiverId).orElse(null));
    }

    private Optional<StandaloneVisits.State> visit(ValueAddedServiceRequest request) {
        return request.visitId() == null ? Optional.empty() : standaloneVisits.find(request.visitId());
    }

    private Long dispatchedVisitId(ValueAddedServiceRequest request) {
        if (request.status() != ValueAddedServiceRequest.Status.DISPATCHED || request.visitId() == null) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_REQUEST_NOT_DISPATCHED",
                    "Only a dispatched value-added service request has a visit to staff.");
        }
        return request.visitId();
    }

    private String serviceName(ValueAddedServiceRequest request) {
        return services.findById(request.valueAddedServiceId()).map(ValueAddedService::name)
                .orElse("Extra service");
    }

    private ValueAddedServiceRequest requireRequest(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Value-added service request", id));
    }

    /**
     * A request as the manager sees it.
     *
     * @param visitStatus the work order's visit state, e.g. SCHEDULED; null before dispatch
     * @param caregiverId who is on the visit; null when nobody is yet
     */
    public record ManagedRequest(ValueAddedServiceRequest request, String serviceName, String visitStatus,
            Long caregiverId) {

        /** Dispatched, but its visit has nobody on it and has not started: the manager's to staff. */
        public boolean needsCaregiver() {
            return request.status() == ValueAddedServiceRequest.Status.DISPATCHED
                    && caregiverId == null && "SCHEDULED".equals(visitStatus);
        }
    }
}
