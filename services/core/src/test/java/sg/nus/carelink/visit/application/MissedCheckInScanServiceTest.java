package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import sg.nus.carelink.incident.application.MissedCheckInIncidentGateway;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.MissedCheckInRepository;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;
import sg.nus.carelink.visit.domain.service.MissedCheckInPolicy;

class MissedCheckInScanServiceTest {
    private final PlatformTransactionManager manager = mock();
    private final VisitCommandRepository visits = mock();
    private final MissedCheckInRepository facts = mock();
    private final MissedCheckInIncidentGateway incidents = mock();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 8, 10, 20);
    private final Clock clock = Clock.fixed(now.atZone(ZoneId.of("Asia/Singapore")).toInstant(), ZoneOffset.UTC);
    private final MissedCheckInPolicy policy = new MissedCheckInPolicy(Duration.ofMinutes(10), Duration.ofDays(1));
    private MissedCheckInScanService service(boolean enabled) {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new MissedCheckInScanService(manager, visits, facts, incidents, policy,
                new MissedCheckInScanService.Settings(enabled, 1), clock);
    }
    private Visit scheduled(long id) {
        return new Visit(id, 2L, 3L, null, null, "Care", now.minusMinutes(20), now.plusHours(1),
                null, null, Visit.Status.SCHEDULED, now.plusHours(2), null, 7, null, null);
    }
    @Test void disabledDoesNotQueryOrLock() {
        var scan = service(false);
        assertThat(scan.scan()).isEqualTo(new MissedCheckInScanService.Outcome(0, 0, 0));
        assertThat(scan.trigger(1L)).isFalse(); verifyNoInteractions(visits, facts, incidents);
    }
    @Test void locksThenRechecksAndAdvancesVersionWithoutPausingBeforeSingleIncident() {
        var scan = service(true); var v = scheduled(1);
        when(visits.lock(1L)).thenReturn(Optional.of(v)); when(incidents.raise(any(), any(), any(), any())).thenReturn(11L);
        assertThat(scan.trigger(1L)).isTrue();
        var order = inOrder(visits, facts, incidents);
        order.verify(visits).lock(1L); order.verify(facts).exists(1L);
        order.verify(visits).save(v);
        order.verify(incidents).raise(2L, 1L, now.minusMinutes(10), now);
        order.verify(facts).save(new sg.nus.carelink.visit.domain.model.MissedCheckInTrigger(1L, 11L, 3L,
                v.scheduledStart(), now.minusMinutes(10), 7, now));
        verify(manager).getTransaction(argThat(tx -> tx.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED
                && tx.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verifyNoMoreInteractions(incidents);
    }
    @Test void disappearedArrivedAndPreviouslyTriggeredAreSkipped() {
        var scan = service(true);
        when(visits.lock(1L)).thenReturn(Optional.empty(), Optional.of(scheduled(1).arrivedAt(now)), Optional.of(scheduled(1)));
        when(facts.exists(1L)).thenReturn(true);
        assertThat(scan.trigger(1L)).isFalse(); assertThat(scan.trigger(1L)).isFalse(); assertThat(scan.trigger(1L)).isFalse();
        verifyNoInteractions(incidents); verify(visits, never()).save(any());
    }
    @Test void nonAdvancingCursorFailsFastInsteadOfLoopingForever() {
        var scan=service(true);
        var a=new MissedCheckInRepository.Candidate(1L,now.minusMinutes(20));
        when(facts.candidates(now.minusDays(1),now.minusMinutes(10),null,1)).thenReturn(List.of(a));
        when(facts.candidates(now.minusDays(1),now.minusMinutes(10),a,1)).thenReturn(List.of(a));
        when(visits.lock(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(scan::scan).isInstanceOf(IllegalStateException.class).hasMessage("SYS03 candidate cursor did not advance");
        verify(visits,times(1)).lock(1L);verifyNoInteractions(incidents);
    }
    @Test void failedFirstPageCannotStarveFollowingPagesAndUpperBoundIsFrozen() {
        var scan = service(true);
        var a = new MissedCheckInRepository.Candidate(1L, now.minusMinutes(20));
        var b = new MissedCheckInRepository.Candidate(2L, now.minusMinutes(20));
        when(facts.candidates(now.minusDays(1), now.minusMinutes(10), null, 1)).thenReturn(List.of(a));
        when(facts.candidates(now.minusDays(1), now.minusMinutes(10), a, 1)).thenReturn(List.of(b));
        when(facts.candidates(now.minusDays(1), now.minusMinutes(10), b, 1)).thenReturn(List.of());
        when(visits.lock(1L)).thenThrow(new IllegalStateException("Synthetic failure"));
        when(visits.lock(2L)).thenReturn(Optional.of(scheduled(2))); when(incidents.raise(any(), any(), any(), any())).thenReturn(11L);
        assertThat(scan.scan()).isEqualTo(new MissedCheckInScanService.Outcome(2, 1, 1));
        verify(manager).rollback(any()); verify(manager).commit(any());
    }
}
