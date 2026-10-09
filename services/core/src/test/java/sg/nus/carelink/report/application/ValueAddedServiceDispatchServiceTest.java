package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.report.application.ValueAddedNotifier.Why;
import sg.nus.carelink.report.application.ValueAddedServiceDispatchService.ManagedRequest;
import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest.Status;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.rostering.application.VisitCover;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;
import sg.nus.carelink.visit.application.StandaloneVisits;
import sg.nus.carelink.visit.application.VisitReassignment;

class ValueAddedServiceDispatchServiceTest {

    private static final LocalDateTime SCHEDULE = LocalDateTime.of(2026, 10, 10, 10, 0);
    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    private ValueAddedServiceRequestRepository requests;
    private ValueAddedServiceRepository services;
    private VisitReassignment visits;
    private StandaloneVisits standaloneVisits;
    private VisitCover cover;
    private ProfileService profiles;
    private ValueAddedNotifier notifier;
    private ValueAddedServiceDispatchService service;

    @BeforeEach
    void setUp() {
        setUp(SCHEDULE.minusDays(3));
    }

    /** {@code now}: the Singapore wall-clock time the service runs at. */
    private void setUp(LocalDateTime now) {
        requests = mock(ValueAddedServiceRequestRepository.class);
        services = mock(ValueAddedServiceRepository.class);
        visits = mock(VisitReassignment.class);
        standaloneVisits = mock(StandaloneVisits.class);
        cover = mock(VisitCover.class);
        profiles = mock(ProfileService.class);
        notifier = mock(ValueAddedNotifier.class);
        IdentityService identity = mock(IdentityService.class);
        when(identity.require("manager")).thenReturn(new AppUser(11L, "manager", "Manager", Set.of(Role.MANAGER), true));
        ValueAddedService escort = new ValueAddedService(2L, "Hospital escort", null,
                ValueAddedService.Status.AVAILABLE, null, null);
        when(services.findAll()).thenReturn(List.of(escort));
        when(services.findById(2L)).thenReturn(Optional.of(escort));
        when(requests.save(any())).thenAnswer(call -> call.getArgument(0));
        Clock clock = Clock.fixed(now.atZone(SINGAPORE).toInstant(), SINGAPORE);
        service = new ValueAddedServiceDispatchService(requests, services, standaloneVisits, visits, cover, identity,
                profiles, notifier, clock);
    }

    @Test
    void listShowsEachRequestWithItsVisitAndWhetherItNeedsACaregiver() {
        ValueAddedServiceRequest unstaffed = request(1L, Status.DISPATCHED, 40L);
        ValueAddedServiceRequest staffed = request(2L, Status.DISPATCHED, 41L);
        ValueAddedServiceRequest pending = request(3L, Status.PENDING_APPROVAL, null);
        when(requests.findAll()).thenReturn(List.of(unstaffed, staffed, pending));
        visitIs(40L, null, "SCHEDULED");
        visitIs(41L, 9L, "SCHEDULED");

        List<ManagedRequest> list = service.listForManager();

        assertThat(list).extracting(ManagedRequest::serviceName).containsOnly("Hospital escort");
        assertThat(list).extracting(ManagedRequest::needsCaregiver).containsExactly(true, false, false);
        assertThat(list.get(1).caregiverId()).isEqualTo(9L);
        assertThat(list.get(2).visitStatus()).isNull();
    }

    @Test
    void aVisitThatStartedUncoveredNoLongerCountsAsNeedingACaregiver() {
        when(requests.findAll()).thenReturn(List.of(request(1L, Status.DISPATCHED, 40L)));
        visitIs(40L, null, "EXCEPTION");

        assertThat(service.listForManager().get(0).needsCaregiver()).isFalse();
    }

    @Test
    void caregiverOptionsComeFromTheDispatchedVisit() {
        found(request(1L, Status.DISPATCHED, 40L));
        List<VisitCover.Option> options = List.of(new VisitCover.Option(9L, "Farah", 1, "Free"));
        when(cover.options(40L)).thenReturn(options);

        assertThat(service.caregiverOptions(1L)).isEqualTo(options);
    }

    @Test
    void aRequestNotYetDispatchedHasNoVisitToStaff() {
        found(request(1L, Status.PENDING_APPROVAL, null));

        assertThatThrownBy(() -> service.caregiverOptions(1L))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("dispatched");
        assertThatThrownBy(() -> service.assignCaregiver(1L, 9L, "manager"))
                .isInstanceOf(BusinessRuleViolation.class);
        verify(cover, never()).cover(anyLong(), anyLong(), anyLong());
    }

    @Test
    void assignCoversTheVisitInTheManagersName() {
        found(request(1L, Status.DISPATCHED, 40L));
        visitIs(40L, 9L, "SCHEDULED");

        ManagedRequest result = service.assignCaregiver(1L, 9L, "manager");

        verify(cover).cover(40L, 9L, 11L);
        assertThat(result.caregiverId()).isEqualTo(9L);
        assertThat(result.needsCaregiver()).isFalse();
    }

    @Test
    void cancellingADispatchedRequestCallsItsVisitOff() {
        found(request(1L, Status.DISPATCHED, 40L));
        visitIs(40L, 9L, "SCHEDULED");

        ManagedRequest result = service.cancel(1L, "manager");

        ArgumentCaptor<VisitReassignment.Change> why = ArgumentCaptor.forClass(VisitReassignment.Change.class);
        verify(visits).callOff(eq(40L), why.capture());
        assertThat(why.getValue().byUserId()).isEqualTo(11L);
        assertThat(why.getValue().reason()).isEqualTo(ValueAddedServiceDispatchService.CANCEL_REASON);
        assertThat(result.request().status()).isEqualTo(Status.CANCELLED);
    }

    @Test
    void cancellingAPendingRequestHasNoVisitToCallOff() {
        found(request(1L, Status.PENDING_APPROVAL, null));

        assertThat(service.cancel(1L, "manager").request().status()).isEqualTo(Status.CANCELLED);
        verify(visits, never()).callOff(anyLong(), any());
    }

    @Test
    void aVisitAlreadyCalledOffIsLeftAsItIs() {
        found(request(1L, Status.DISPATCHED, 40L));
        visitIs(40L, 9L, "CANCELLED");

        assertThat(service.cancel(1L, "manager").request().status()).isEqualTo(Status.CANCELLED);
        verify(visits, never()).callOff(anyLong(), any());
    }

    @Test
    void aVisitUnderWayCannotBeCancelledFromHere() {
        found(request(1L, Status.DISPATCHED, 40L));
        visitIs(40L, 9L, "IN_PROGRESS");

        assertThatThrownBy(() -> service.cancel(1L, "manager"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("IN_PROGRESS");
        verify(requests, never()).save(any());
    }

    @Test
    void aFinishedRequestCannotBeCancelled() {
        found(request(1L, Status.COMPLETED, 40L));

        assertThatThrownBy(() -> service.cancel(1L, "manager")).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void anUnknownRequestIsNotFound() {
        when(requests.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L, "manager")).isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void settlingFollowsEachDispatchedRequestsVisit() {
        when(requests.findByStatus(Status.DISPATCHED)).thenReturn(List.of(
                request(1L, Status.DISPATCHED, 40L),
                request(2L, Status.DISPATCHED, 41L),
                request(3L, Status.DISPATCHED, 42L),
                request(4L, Status.DISPATCHED, 43L)));
        visitIs(40L, 9L, "VERIFIED");
        visitIs(41L, 9L, "CANCELLED");
        visitIs(42L, 9L, "IN_PROGRESS");
        when(standaloneVisits.find(43L)).thenReturn(Optional.empty());

        assertThat(service.settleWithVisits()).isEqualTo(2);

        ArgumentCaptor<ValueAddedServiceRequest> saved = ArgumentCaptor.forClass(ValueAddedServiceRequest.class);
        verify(requests, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ValueAddedServiceRequest::id, ValueAddedServiceRequest::status)
                .containsExactly(tuple(1L, Status.COMPLETED), tuple(2L, Status.CANCELLED));
    }

    private void found(ValueAddedServiceRequest request) {
        when(requests.findById(request.id())).thenReturn(Optional.of(request));
    }

    private void visitIs(Long visitId, Long caregiverId, String status) {
        visitIs(visitId, caregiverId, status, false);
    }

    private void visitIs(Long visitId, Long caregiverId, String status, boolean checkedIn) {
        when(standaloneVisits.find(visitId)).thenReturn(Optional.of(
                new StandaloneVisits.State(visitId, caregiverId, status, checkedIn)));
    }

    private static ValueAddedServiceRequest request(Long id, Status status, Long visitId) {
        return new ValueAddedServiceRequest(id, 7L, 2L, null, status == Status.PENDING_APPROVAL ? null : 3L,
                visitId, SCHEDULE, null, status, null, null, null);
    }

    @Test
    void assigningTellsTheCaregiverAndFamily() {
        found(request(1L, Status.DISPATCHED, 40L));
        visitIs(40L, 9L, "SCHEDULED");

        service.assignCaregiver(1L, 9L, "manager");

        verify(notifier).caregiverAssigned(any(), eq("Hospital escort"), eq(9L));
    }

    @Test
    void cancellingTellsTheFamilyAndWhoeverWasOnTheVisit() {
        found(request(1L, Status.DISPATCHED, 40L));
        visitIs(40L, 9L, "SCHEDULED");

        service.cancel(1L, "manager");

        verify(notifier).cancelled(any(), eq("Hospital escort"), eq(9L), eq(Why.BY_MANAGER));
    }

    @Test
    void theElderWithdrawsOnlyTheirOwnRequest() {
        when(profiles.requireElderByUserId(30L)).thenReturn(elder(7L));
        found(request(1L, Status.PENDING_APPROVAL, null));
        when(requests.findById(2L)).thenReturn(Optional.of(new ValueAddedServiceRequest(2L, 8L, 2L, null, null, null,
                SCHEDULE, null, Status.PENDING_APPROVAL, null, null, null)));

        assertThat(service.cancelForElderUser(30L, 1L).status()).isEqualTo(Status.CANCELLED);
        verify(notifier).cancelled(any(), eq("Hospital escort"), eq(null), eq(Why.BY_ELDER));
        assertThatThrownBy(() -> service.cancelForElderUser(30L, 2L)).isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void anExceptionBeforeAnybodyCheckedInClosesTheRequestAndTellsTheFamily() {
        when(requests.findByStatus(Status.DISPATCHED)).thenReturn(List.of(
                request(1L, Status.DISPATCHED, 40L),
                request(2L, Status.DISPATCHED, 41L)));
        visitIs(40L, null, "EXCEPTION", false);
        visitIs(41L, 9L, "EXCEPTION", true);

        assertThat(service.settleWithVisits()).isEqualTo(1);

        ArgumentCaptor<ValueAddedServiceRequest> saved = ArgumentCaptor.forClass(ValueAddedServiceRequest.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().id()).as("a visit started before its exception is left to the incident").isEqualTo(1L);
        assertThat(saved.getValue().status()).isEqualTo(Status.CANCELLED);
        verify(notifier).cancelled(any(), eq("Hospital escort"), eq(null), eq(Why.NOT_PROVIDED));
    }

    @Test
    void anUnansweredRequestIsRemindedWithinADayAndLapsesAtTheCutoff() {
        setUp(SCHEDULE.minusHours(5));
        ValueAddedServiceRequest dueSoon = request(1L, Status.PENDING_APPROVAL, null);
        ValueAddedServiceRequest tooLate = new ValueAddedServiceRequest(2L, 7L, 2L, null, null, null,
                SCHEDULE.minusHours(5).plusMinutes(20), null, Status.PENDING_APPROVAL, null, null, null);
        ValueAddedServiceRequest farOff = new ValueAddedServiceRequest(3L, 7L, 2L, null, null, null,
                SCHEDULE.plusDays(3), null, Status.PENDING_APPROVAL, null, null, null);
        when(requests.findByStatus(Status.PENDING_APPROVAL)).thenReturn(List.of(dueSoon, tooLate, farOff));

        assertThat(service.followUpUnanswered()).isEqualTo(1);

        verify(notifier).reminder(dueSoon, "Hospital escort");
        verify(notifier, never()).reminder(eq(farOff), any());
        verify(notifier).cancelled(any(), eq("Hospital escort"), eq(null), eq(Why.NOT_ANSWERED));
        ArgumentCaptor<ValueAddedServiceRequest> saved = ArgumentCaptor.forClass(ValueAddedServiceRequest.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(2L);
        assertThat(saved.getValue().status()).isEqualTo(Status.CANCELLED);
    }

    private static Elder elder(Long id) {
        return new Elder(id, 30L, "Mdm Tan", null, null, null, null, null, null, null, null, null,
                Elder.ContinuityPreference.PREFERRED, null, null, null);
    }
}
