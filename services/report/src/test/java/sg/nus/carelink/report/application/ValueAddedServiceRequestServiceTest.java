package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

class ValueAddedServiceRequestServiceTest {

    private static final ZoneId ZONE =
            ZoneId.of(
                    "Asia/Singapore"
            );

    private static final Clock CLOCK =
            Clock.fixed(
                    Instant.parse(
                            "2026-10-07T09:00:00Z"
                    ),
                    ZONE
            );

    private static final LocalDateTime NOW =
            LocalDateTime.of(
                    2026,
                    10,
                    7,
                    17,
                    0
            );

    private static final LocalDateTime SCHEDULE =
            LocalDateTime.of(
                    2026,
                    10,
                    10,
                    10,
                    0
            );

    private Accounts accounts;
    private Elders elders;
    private FamilyMembers families;
    private FamilyAccessQuery familyAccess;
    private ValueAddedServiceRepository services;
    private ValueAddedServiceRequestRepository requests;
    private StandaloneVisits visits;
    private ValueAddedVisitAssignment assignment;
    private ValueAddedManagerAlert managerAlert;
    private ValueAddedNotifier notifier;

    private ValueAddedServiceRequestService service;

    @BeforeEach
    void setUp() {
        accounts =
                mock(
                        Accounts.class
                );

        elders =
                mock(
                        Elders.class
                );

        families =
                mock(
                        FamilyMembers.class
                );

        familyAccess =
                mock(
                        FamilyAccessQuery.class
                );

        services =
                mock(
                        ValueAddedServiceRepository.class
                );

        requests =
                mock(
                        ValueAddedServiceRequestRepository.class
                );

        visits =
                mock(
                        StandaloneVisits.class
                );

        assignment = mock(ValueAddedVisitAssignment.class);
        managerAlert = mock(ValueAddedManagerAlert.class);
        notifier = mock(ValueAddedNotifier.class);
        service =
                new ValueAddedServiceRequestService(
                        accounts,
                        elders,
                        families,
                        familyAccess,
                        services,
                        requests,
                        visits,
                        CLOCK,
                        assignment,
                        managerAlert,
                        notifier
                );
    }

    // ---------------------------------------------------------
    // Catalogue
    // ---------------------------------------------------------

    @Test
    void listsAvailableCatalogueServices() {
        ValueAddedService catalogue =
                availableService();

        when(
                services.findAvailable()
        ).thenReturn(
                List.of(catalogue)
        );

        assertThat(
                service.availableServices()
        ).containsExactly(
                catalogue
        );

        verify(services)
                .findAvailable();
    }

    @Test
    void requireServiceReturnsCatalogueService() {
        ValueAddedService catalogue =
                availableService();

        when(
                services.findById(
                        2L
                )
        ).thenReturn(
                Optional.of(catalogue)
        );

        assertThat(
                service.requireService(
                        2L
                )
        ).isEqualTo(
                catalogue
        );
    }

    @Test
    void requireServiceRejectsUnknownService() {
        when(
                services.findById(
                        999L
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(() ->
                service.requireService(
                        999L
                )
        ).isInstanceOf(
                ResourceNotFound.class
        );
    }

    // ---------------------------------------------------------
    // EL02 - Elder
    // ---------------------------------------------------------

    @Test
    void listsRequestsForAuthenticatedElderProfile() {
        Long elder =
                elder();

        ValueAddedServiceRequest request =
                pendingRequest();

        when(
                elders.requireElderIdOfUser(
                        1L
                )
        ).thenReturn(
                elder
        );

        when(
                requests.findByElderId(
                        10L
                )
        ).thenReturn(
                List.of(request)
        );

        assertThat(
                service.listForElderUser(
                        1L
                )
        ).containsExactly(
                request
        );

        verify(elders)
                .requireElderIdOfUser(
                        1L
                );

        verify(requests)
                .findByElderId(
                        10L
                );
    }

    @Test
    void elderCreatesPendingApprovalRequest() {
        when(
                elders.requireElderIdOfUser(
                        1L
                )
        ).thenReturn(
                elder()
        );

        when(
                services.findById(
                        2L
                )
        ).thenReturn(
                Optional.of(
                        availableService()
                )
        );

        when(
                requests.save(
                        any(
                                ValueAddedServiceRequest.class
                        )
                )
        ).thenAnswer(invocation -> {
            ValueAddedServiceRequest value =
                    invocation.getArgument(0);

            return new ValueAddedServiceRequest(
                    50L,
                    value.elderId(),
                    value.valueAddedServiceId(),
                    value.requestedByFamilyMemberId(),
                    value.approvingFamilyMemberId(),
                    value.visitId(),
                    value.requestedSchedule(),
                    value.specialInstructions(),
                    value.status(),
                    value.decidedAt(),
                    NOW,
                    NOW
            );
        });

        ValueAddedServiceRequest saved =
                service.requestForElderUser(
                        1L,
                        2L,
                        SCHEDULE,
                        "  Please accompany me.  "
                );

        assertThat(saved.id())
                .isEqualTo(
                        50L
                );

        assertThat(saved.elderId())
                .isEqualTo(
                        10L
                );

        assertThat(saved.valueAddedServiceId())
                .isEqualTo(
                        2L
                );

        assertThat(saved.requestedSchedule())
                .isEqualTo(
                        SCHEDULE
                );

        assertThat(saved.status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.PENDING_APPROVAL
                );

        assertThat(saved.requestedByFamilyMemberId())
                .isNull();

        assertThat(saved.approvingFamilyMemberId())
                .isNull();

        assertThat(saved.visitId())
                .isNull();

        assertThat(saved.specialInstructions())
                .isEqualTo(
                        "Please accompany me."
                );

        verify(requests)
                .save(
                        any(
                                ValueAddedServiceRequest.class
                        )
                );

        verifyNoInteractions(
                visits
        );
    }

    @Test
    void elderCannotRequestUnavailableService() {
        ValueAddedService unavailable =
                new ValueAddedService(
                        2L,
                        "Hospital escort",
                        null,
                        ValueAddedService.Status.UNAVAILABLE,
                        null,
                        null
                );

        when(
                elders.requireElderIdOfUser(
                        1L
                )
        ).thenReturn(
                elder()
        );

        when(
                services.findById(
                        2L
                )
        ).thenReturn(
                Optional.of(
                        unavailable
                )
        );

        assertThatThrownBy(() ->
                service.requestForElderUser(
                        1L,
                        2L,
                        SCHEDULE,
                        null
                )
        )
                .isInstanceOf(
                        BusinessRuleViolation.class
                )
                .extracting(error ->
                        ((BusinessRuleViolation) error)
                                .code()
                )
                .isEqualTo(
                        "VALUE_ADDED_SERVICE_UNAVAILABLE"
                );

        verify(requests, never())
                .save(any());

        verifyNoInteractions(
                visits
        );
    }

    @Test
    void unknownCatalogueServiceIsRejected() {
        when(
                elders.requireElderIdOfUser(
                        1L
                )
        ).thenReturn(
                elder()
        );

        when(
                services.findById(
                        999L
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(() ->
                service.requestForElderUser(
                        1L,
                        999L,
                        SCHEDULE,
                        null
                )
        ).isInstanceOf(
                ResourceNotFound.class
        );

        verify(requests, never())
                .save(any());

        verifyNoInteractions(
                visits
        );
    }

    // ---------------------------------------------------------
    // FM08 - Read
    // ---------------------------------------------------------

    @Test
    void familyListsRequestsOnlyAfterReadableAccessCheck() {
        when(
                requests.findByElderId(
                        10L
                )
        ).thenReturn(
                List.of(
                        pendingRequest()
                )
        );

        assertThat(
                service.listForFamily(
                        "family_test",
                        10L
                )
        ).hasSize(
                1
        );

        InOrder order =
                inOrder(
                        familyAccess,
                        requests
                );

        order.verify(
                familyAccess
        ).requireReadableElder(
                "family_test",
                10L
        );

        order.verify(
                requests
        ).findByElderId(
                10L
        );
    }

    // ---------------------------------------------------------
    // FM08 - Reject
    // ---------------------------------------------------------

    @Test
    void familyRejectsPendingRequestAfterWritableAccessCheck() {
        prepareFamilyDecision();

        when(
                requests.save(
                        any(
                                ValueAddedServiceRequest.class
                        )
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        ValueAddedServiceRequest result =
                service.decideForFamily(
                        "family_test",
                        5L,
                        ValueAddedServiceRequestService.Decision.REJECTED
                );

        verify(familyAccess)
                .requireWritableElder(
                        "family_test",
                        10L
                );

        assertThat(result.status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.REJECTED
                );

        assertThat(result.approvingFamilyMemberId())
                .isEqualTo(
                        20L
                );

        assertThat(result.visitId())
                .isNull();

        assertThat(result.decidedAt())
                .isEqualTo(
                        NOW
                );

        verify(visits, never()).schedule(any());
    }

    // ---------------------------------------------------------
    // FM08 - Approve / Dispatch
    // ---------------------------------------------------------

    @Test
    void familyApprovalRequiresWritableAccess() {
        prepareFamilyDecision();

        when(
                services.findById(
                        2L
                )
        ).thenReturn(
                Optional.of(
                        availableService()
                )
        );

        when(visits.schedule(any())).thenReturn(77L);

        when(
                requests.save(
                        any(
                                ValueAddedServiceRequest.class
                        )
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        service.decideForFamily(
                "family_test",
                5L,
                ValueAddedServiceRequestService.Decision.APPROVED
        );

        verify(familyAccess)
                .requireWritableElder(
                        "family_test",
                        10L
                );
    }

    @Test
    void familyApprovalCreatesScheduledVisitAndDispatchesRequest() {
        prepareFamilyDecision();

        when(
                services.findById(
                        2L
                )
        ).thenReturn(
                Optional.of(
                        availableService()
                )
        );

        when(visits.schedule(any())).thenReturn(77L);

        when(
                requests.save(
                        any(
                                ValueAddedServiceRequest.class
                        )
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        ValueAddedServiceRequest result =
                service.decideForFamily(
                        "family_test",
                        5L,
                        ValueAddedServiceRequestService.Decision.APPROVED
                );

        assertThat(result.status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.DISPATCHED
                );

        assertThat(result.visitId())
                .isEqualTo(
                        77L
                );

        assertThat(result.approvingFamilyMemberId())
                .isEqualTo(
                        20L
                );

        assertThat(result.decidedAt())
                .isEqualTo(
                        NOW
                );

        verify(visits).schedule(org.mockito.ArgumentMatchers.argThat(visit ->
                visit.elderId().equals(10L)
                        && visit.caregiverId() == null
                        && visit.serviceType().equals("Hospital escort")
                        && visit.start().equals(SCHEDULE)
                        && visit.end().equals(SCHEDULE.plusHours(1))
                        && visit.instructions().equals("Need assistance")));

        when(visits.schedule(any())).thenReturn(77L);

        when(
                requests.save(
                        any(
                                ValueAddedServiceRequest.class
                        )
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        service.decideForFamily(
                "family_test",
                5L,
                ValueAddedServiceRequestService.Decision.APPROVED
        );

        InOrder order =
                inOrder(
                        familyAccess,
                        visits,
                        requests
                );

        order.verify(
                familyAccess
        ).requireWritableElder(
                "family_test",
                10L
        );

        order.verify(
                visits
        ).schedule(
                any()
        );

        order.verify(
                requests
        ).save(
                any(
                        ValueAddedServiceRequest.class
                )
        );
    }

    // ---------------------------------------------------------
    // FM08 - Invalid decisions
    // ---------------------------------------------------------

    @Test
    void alreadyDecidedRequestCannotBeDecidedAgain() {
        prepareFamily();

        ValueAddedServiceRequest rejected =
                pendingRequest()
                        .reject(
                                20L,
                                LocalDateTime.of(
                                        2026,
                                        10,
                                        7,
                                        16,
                                        0
                                )
                        );

        when(
                requests.findById(
                        5L
                )
        ).thenReturn(
                Optional.of(
                        rejected
                )
        );

        assertThatThrownBy(() ->
                service.decideForFamily(
                        "family_test",
                        5L,
                        ValueAddedServiceRequestService.Decision.REJECTED
                )
        )
                .isInstanceOf(
                        BusinessRuleViolation.class
                )
                .extracting(error ->
                        ((BusinessRuleViolation) error)
                                .code()
                )
                .isEqualTo(
                        "VALUE_ADDED_SERVICE_REQUEST_ALREADY_DECIDED"
                );

        verify(visits, never()).schedule(any());
    }

    @Test
    void unknownRequestCannotBeDecided() {
        prepareFamily();

        when(
                requests.findById(
                        999L
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(() ->
                service.decideForFamily(
                        "family_test",
                        999L,
                        ValueAddedServiceRequestService.Decision.APPROVED
                )
        ).isInstanceOf(
                ResourceNotFound.class
        );

        verifyNoInteractions(
                familyAccess
        );

        verify(visits, never()).schedule(any());
    }

    // ---------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------

    private void prepareFamilyDecision() {
        prepareFamily();

        when(
                requests.findById(
                        5L
                )
        ).thenReturn(
                Optional.of(
                        pendingRequest()
                )
        );
    }

    private void prepareFamily() {
        when(
                accounts.idOf(
                        "family_test"
                )
        ).thenReturn(
                3L
        );

        when(
                families.findIdByUserId(
                        3L
                )
        ).thenReturn(
                Optional.of(
                        20L
                )
        );
    }

    /** The elder of elder account 1. */
    private Long elder() {
        return 10L;
    }

    private ValueAddedService availableService() {
        return new ValueAddedService(
                2L,
                "Hospital escort",
                "Escort to medical appointments",
                ValueAddedService.Status.AVAILABLE,
                null,
                null
        );
    }

    private ValueAddedServiceRequest pendingRequest() {
        return new ValueAddedServiceRequest(
                5L,
                10L,
                2L,
                null,
                null,
                null,
                SCHEDULE,
                "Need assistance",
                ValueAddedServiceRequest.Status.PENDING_APPROVAL,
                null,
                LocalDateTime.of(
                        2026,
                        10,
                        7,
                        12,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        10,
                        7,
                        12,
                        0
                )
        );
    }

    @Test
    void approvalAssignsEligiblePrimaryCaregiverAndAlertsManagers() {
        prepareFamilyDecision();
        when(services.findById(2L)).thenReturn(Optional.of(availableService()));
        when(assignment.chooseCaregiver(10L, SCHEDULE, SCHEDULE.plusHours(1))).thenReturn(Optional.of(42L));
        when(visits.schedule(any())).thenReturn(77L);
        when(requests.save(any(ValueAddedServiceRequest.class))).thenAnswer(call -> call.getArgument(0));

        ValueAddedServiceRequest result = service.decideForFamily("family_test", 5L,
                ValueAddedServiceRequestService.Decision.APPROVED);

        org.mockito.ArgumentCaptor<StandaloneVisits.NewVisit> saved = org.mockito.ArgumentCaptor.forClass(StandaloneVisits.NewVisit.class);
        verify(visits).schedule(saved.capture());
        assertThat(saved.getValue().caregiverId()).isEqualTo(42L);
        assertThat(result.visitId()).isEqualTo(77L);
        verify(managerAlert).approved(77L, 10L, "Hospital escort", SCHEDULE, 42L);
    }

    @Test
    void approvalWithoutEligiblePrimaryCaregiverAlertsManagersForManualAssignment() {
        prepareFamilyDecision();
        when(services.findById(2L)).thenReturn(Optional.of(availableService()));
        when(assignment.chooseCaregiver(10L, SCHEDULE, SCHEDULE.plusHours(1))).thenReturn(Optional.empty());
        when(visits.schedule(any())).thenReturn(77L);
        when(requests.save(any(ValueAddedServiceRequest.class))).thenAnswer(call -> call.getArgument(0));

        service.decideForFamily("family_test", 5L,
                ValueAddedServiceRequestService.Decision.APPROVED);

        org.mockito.ArgumentCaptor<StandaloneVisits.NewVisit> saved = org.mockito.ArgumentCaptor.forClass(StandaloneVisits.NewVisit.class);
        verify(visits).schedule(saved.capture());
        assertThat(saved.getValue().caregiverId()).isNull();
        verify(managerAlert).approved(77L, 10L, "Hospital escort", SCHEDULE, null);
    }

    @Test
    void rejectionNeverAssignsOrAlertsManagers() {
        prepareFamilyDecision();
        when(requests.save(any(ValueAddedServiceRequest.class))).thenAnswer(call -> call.getArgument(0));

        service.decideForFamily("family_test", 5L,
                ValueAddedServiceRequestService.Decision.REJECTED);

        verifyNoInteractions(assignment, managerAlert);
        verify(visits, never()).schedule(any());
    }

    // ---------------------------------------------------------
    // Notice, lateness, family requests and who is told
    // ---------------------------------------------------------

    @Test
    void elderRequestNeedsTwoHoursNoticeAndTellsTheFamily() {
        when(elders.requireElderIdOfUser(1L)).thenReturn(elder());
        when(services.findById(2L)).thenReturn(Optional.of(availableService()));
        when(requests.save(any(ValueAddedServiceRequest.class))).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.requestForElderUser(1L, 2L, NOW.plusMinutes(119), null))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting(error -> ((BusinessRuleViolation) error).code())
                .isEqualTo("VALUE_ADDED_SERVICE_TOO_SOON");
        assertThatThrownBy(() -> service.requestForElderUser(1L, 2L, NOW.minusDays(1), null))
                .isInstanceOf(BusinessRuleViolation.class);
        verify(requests, never()).save(any());

        ValueAddedServiceRequest saved = service.requestForElderUser(1L, 2L, NOW.plusHours(2), null);

        verify(notifier).requested(saved, "Hospital escort");
    }

    @Test
    void approvalTooCloseToTheRequestedTimeIsRefused() {
        prepareFamily();
        ValueAddedServiceRequest soon = new ValueAddedServiceRequest(5L, 10L, 2L, null, null, null,
                NOW.plusMinutes(20), null, ValueAddedServiceRequest.Status.PENDING_APPROVAL, null, null, null);
        when(requests.findById(5L)).thenReturn(Optional.of(soon));
        when(services.findById(2L)).thenReturn(Optional.of(availableService()));

        assertThatThrownBy(() -> service.decideForFamily("family_test", 5L,
                ValueAddedServiceRequestService.Decision.APPROVED))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting(error -> ((BusinessRuleViolation) error).code())
                .isEqualTo("VALUE_ADDED_SERVICE_TOO_LATE");
        verify(visits, never()).schedule(any());
    }

    @Test
    void theVisitLastsAsLongAsTheServiceAndTheCaregiverIsTold() {
        prepareFamilyDecision();
        ValueAddedService escort = new ValueAddedService(2L, "Hospital escort", null, 180,
                ValueAddedService.Status.AVAILABLE, null, null);
        when(services.findById(2L)).thenReturn(Optional.of(escort));
        when(assignment.chooseCaregiver(10L, SCHEDULE, SCHEDULE.plusHours(3))).thenReturn(Optional.of(42L));
        when(visits.schedule(any())).thenReturn(77L);
        when(requests.save(any(ValueAddedServiceRequest.class))).thenAnswer(call -> call.getArgument(0));

        ValueAddedServiceRequest result = service.decideForFamily("family_test", 5L,
                ValueAddedServiceRequestService.Decision.APPROVED);

        verify(visits).schedule(org.mockito.ArgumentMatchers.argThat(visit -> visit.end().equals(SCHEDULE.plusHours(3))));
        verify(notifier).caregiverAssigned(result, "Hospital escort", 42L);
    }

    @Test
    void familyRequestIsTheirApprovalAndIsDispatchedAtOnce() {
        prepareFamily();
        when(services.findById(2L)).thenReturn(Optional.of(availableService()));
        when(assignment.chooseCaregiver(any(), any(), any())).thenReturn(Optional.empty());
        when(visits.schedule(any())).thenReturn(77L);
        when(requests.save(any(ValueAddedServiceRequest.class))).thenAnswer(call -> call.getArgument(0));

        ValueAddedServiceRequest result = service.requestForFamily("family_test", 10L, 2L, SCHEDULE, " Bring the wheelchair ");

        verify(familyAccess).requireWritableElder("family_test", 10L);
        assertThat(result.status()).isEqualTo(ValueAddedServiceRequest.Status.DISPATCHED);
        assertThat(result.requestedByFamilyMemberId()).isEqualTo(20L);
        assertThat(result.approvingFamilyMemberId()).isEqualTo(20L);
        assertThat(result.visitId()).isEqualTo(77L);
        verify(visits).schedule(org.mockito.ArgumentMatchers.argThat(visit ->
                "Bring the wheelchair".equals(visit.instructions())));
        verify(notifier, never()).requested(any(), any());
    }

    @Test
    void familyRequestNeedsEnoughNoticeToo() {
        prepareFamily();
        when(services.findById(2L)).thenReturn(Optional.of(availableService()));

        assertThatThrownBy(() -> service.requestForFamily("family_test", 10L, 2L, NOW.plusMinutes(30), null))
                .isInstanceOf(BusinessRuleViolation.class);
        verify(requests, never()).save(any());
    }
}
