package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.incident.domain.repository.IncidentLogRepository;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;

class MissedCheckInEntryTest {
    private final IncidentRepository repository=mock();
    private final IncidentLogRepository timeline=mock();
    private final EscalationService escalation=mock();
    private final IncidentService service=new IncidentService(repository,timeline,escalation,Clock.systemUTC());
    private final LocalDateTime at=LocalDateTime.of(2026,10,8,10,15,1);
    private final LocalDateTime due=at.minusSeconds(1);
    private final Incident fact=Incident.raisedForMissedCheckIn(1L,2L,due,at);
    private final Incident saved=new InMemoryIncidentRepository().save(fact);

    @Test void savesSystemFactAndTimelineThenRoutesExactlyOnce() {
        when(repository.save(fact)).thenReturn(saved);
        when(escalation.routeNewIncident(saved)).thenReturn(saved);
        assertThat(service.raiseForMissedCheckIn(1L,2L,due,at)).isSameAs(saved);
        var order=inOrder(repository,timeline,escalation);
        order.verify(repository).save(fact);
        order.verify(timeline).save(IncidentLog.entry(saved.id(),"system",IncidentLog.Action.REPORTED,
                "assigned caregiver has not checked in after the allowed lateness threshold",at));
        order.verify(escalation).routeNewIncident(saved);
        verifyNoMoreInteractions(repository,timeline,escalation);
    }
    @Test void failedFactNeverWritesTimelineOrRoutes() {
        when(repository.save(fact)).thenThrow(new IllegalStateException("Synthetic save failure"));
        assertThatThrownBy(()->service.raiseForMissedCheckIn(1L,2L,due,at)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(timeline,escalation);
    }
    @Test void failedTimelineNeverRoutes() {
        when(repository.save(fact)).thenReturn(saved);
        when(timeline.save(any())).thenThrow(new IllegalStateException("Synthetic timeline failure"));
        assertThatThrownBy(()->service.raiseForMissedCheckIn(1L,2L,due,at)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(escalation);
    }
    @Test void gatewayDelegatesOnlyToCentralEntryAndReturnsItsId() {
        var central=mock(IncidentService.class);
        when(central.raiseForMissedCheckIn(1L,2L,due,at)).thenReturn(saved);
        assertThat(new MissedCheckInIncidentService(central).raise(1L,2L,due,at)).isEqualTo(saved.id());
        verify(central).raiseForMissedCheckIn(1L,2L,due,at);
        verifyNoMoreInteractions(central);
    }
}
