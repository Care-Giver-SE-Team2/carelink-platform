package sg.nus.carelink.report.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.StandaloneVisits;

/**
 * Application service for UC-EL02 and UC-FM08: the elder or a family member asks, the family
 * answers, and approval dispatches the work order - a standalone visit the length of the
 * service, with the request's instructions for the caregiver - through the visit module.
 */
@Service
@Transactional
public class ValueAddedServiceRequestService {
    private final IdentityService identity;
    private final ProfileService profiles;
    private final FamilyMemberRepository families;
    private final FamilyAccessQuery familyAccess;
    private final ValueAddedServiceRepository services;
    private final ValueAddedServiceRequestRepository requests;
    private final StandaloneVisits visits;
    private final Clock clock;
    private final ValueAddedVisitAssignment assignment;
    private final ValueAddedManagerAlert managerAlert;
    private final ValueAddedNotifier notifier;

    @Autowired
    public ValueAddedServiceRequestService(
            IdentityService identity,
            ProfileService profiles,
            FamilyMemberRepository families,
            FamilyAccessQuery familyAccess,
            ValueAddedServiceRepository services,
            ValueAddedServiceRequestRepository requests,
            StandaloneVisits visits,
            Clock clock,
            ValueAddedVisitAssignment assignment,
            ValueAddedManagerAlert managerAlert,
            ValueAddedNotifier notifier) {
        this.identity = identity;
        this.profiles = profiles;
        this.families = families;
        this.familyAccess = familyAccess;
        this.services = services;
        this.requests = requests;
        this.visits = visits;
        this.clock = clock;
        this.assignment = assignment;
        this.managerAlert = managerAlert;
        this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public List<ValueAddedService> availableServices() {
        return services.findAvailable();
    }

    /** UC-EL02: list the current elder's own requests. */
    @Transactional(readOnly = true)
    public List<ValueAddedServiceRequest> listForElderUser(Long userId) {
        Long elderId = profiles.requireElderByUserId(userId).id();
        return requests.findByElderId(elderId);
    }

    /** UC-EL02: create a PENDING_APPROVAL request, and ask the family to answer it. */
    public ValueAddedServiceRequest requestForElderUser(
            Long userId,
            Long serviceId,
            LocalDateTime requestedSchedule,
            String specialInstructions) {
        Long elderId = profiles.requireElderByUserId(userId).id();
        ValueAddedService service = requireAvailable(serviceId);
        ValueAddedServiceRequest.requireEnoughNotice(requestedSchedule, now());
        ValueAddedServiceRequest saved = requests.save(ValueAddedServiceRequest.requestedByElder(
                elderId, serviceId, requestedSchedule, specialInstructions));
        notifier.requested(saved, service.name());
        return saved;
    }

    /**
     * UC-FM08 on the elder's behalf: a family member who may act for the elder asks for the
     * service. Their asking is their approval, so it is dispatched at once.
     */
    public ValueAddedServiceRequest requestForFamily(
            String username,
            Long elderId,
            Long serviceId,
            LocalDateTime requestedSchedule,
            String specialInstructions) {
        FamilyMember family = requireFamily(username);
        familyAccess.requireWritableElder(username, elderId);
        ValueAddedService service = requireAvailable(serviceId);
        ValueAddedServiceRequest.requireEnoughNotice(requestedSchedule, now());
        ValueAddedServiceRequest pending = requests.save(ValueAddedServiceRequest.requestedByFamily(
                elderId, serviceId, family.id(), requestedSchedule, specialInstructions));
        return dispatch(pending, family.id(), service);
    }

    /** UC-FM08: list requests for an elder that the current family account may read. */
    @Transactional(readOnly = true)
    public List<ValueAddedServiceRequest> listForFamily(String username, Long elderId) {
        familyAccess.requireReadableElder(username, elderId);
        return requests.findByElderId(elderId);
    }

    /**
     * UC-FM08: approve or reject. Approval creates a scheduled work order, attempts
     * to assign the primary caregiver and alerts managers about the assignment result.
     */
    public ValueAddedServiceRequest decideForFamily(String username, Long requestId, Decision decision) {
        FamilyMember family = requireFamily(username);
        ValueAddedServiceRequest request = requireRequest(requestId);
        familyAccess.requireWritableElder(username, request.elderId());

        if (decision == Decision.REJECTED) {
            return requests.save(request.reject(family.id(), now()));
        }

        ValueAddedService service = requireService(request.valueAddedServiceId());
        if (request.requestedSchedule() == null) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_SCHEDULE_REQUIRED",
                    "A requested schedule is required before the service can be approved.");
        }
        // Fail-fast if the request was already decided, before any work order is created.
        if (request.status() != ValueAddedServiceRequest.Status.PENDING_APPROVAL) {
            throw new BusinessRuleViolation("VALUE_ADDED_SERVICE_REQUEST_ALREADY_DECIDED",
                    "Only a pending value-added service request can be decided.");
        }
        if (!request.answerableAt(now())) {
            throw new BusinessRuleViolation("VALUE_ADDED_SERVICE_TOO_LATE",
                    "It is too close to the requested time to arrange this service. Ask for another time.");
        }
        return dispatch(request, family.id(), service);
    }

    /**
     * Approves the request in the family member's name and books its visit: the primary
     * caregiver if they are free for the whole of it, else nobody, for a manager to staff.
     */
    private ValueAddedServiceRequest dispatch(ValueAddedServiceRequest request, Long familyMemberId,
            ValueAddedService service) {
        LocalDateTime start = request.requestedSchedule();
        LocalDateTime end = start.plus(service.duration());
        Long caregiverId = assignment.chooseCaregiver(request.elderId(), start, end).orElse(null);
        Long visitId = visits.schedule(new StandaloneVisits.NewVisit(
                request.elderId(), caregiverId, service.name(), start, end, request.specialInstructions()));
        ValueAddedServiceRequest saved = requests.save(request.approveAndDispatch(familyMemberId, visitId, now()));
        // Same transaction: if notification persistence fails, approval is rolled back.
        managerAlert.approved(visitId, request.elderId(), service.name(), start, caregiverId);
        if (caregiverId != null) {
            notifier.caregiverAssigned(saved, service.name(), caregiverId);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public ValueAddedService requireService(Long id) {
        return services.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Value-added service", id));
    }

    private ValueAddedService requireAvailable(Long serviceId) {
        ValueAddedService service = requireService(serviceId);
        if (!service.available()) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_UNAVAILABLE",
                    "The selected value-added service is not currently available.");
        }
        return service;
    }

    private FamilyMember requireFamily(String username) {
        var account = identity.require(username);
        return families.findByUserId(account.id())
                .orElseThrow(() -> new ResourceNotFound("Family member for user", account.id()));
    }

    private ValueAddedServiceRequest requireRequest(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Value-added service request", id));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    public enum Decision {
        APPROVED, REJECTED
    }
}
