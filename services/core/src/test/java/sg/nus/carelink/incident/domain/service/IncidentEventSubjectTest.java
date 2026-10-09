package sg.nus.carelink.incident.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.incident.domain.model.FamilyAlertEvent;
import sg.nus.carelink.incident.domain.model.FamilyUrgentNotice;
import sg.nus.carelink.incident.domain.model.Incident;

/** Observer wiring and safe facts without Spring. @author Wang Zhili */
class IncidentEventSubjectTest {
	private final UUID id = UUID.randomUUID();
	private final OffsetDateTime at = OffsetDateTime.parse("2026-10-07T08:00:00.123456789Z");
	private FamilyAlertEvent raised() { return new FamilyAlertEvent(id, FamilyAlertEvent.Type.INCIDENT_RAISED, 601L, 101L, at); }

	@Test void allRegisteredObserversReceiveTheSameEventEvenAfterOneFails() {
		var count = new AtomicInteger();
		var failure = new IllegalStateException("Unavailable observer");
		List<IncidentEventObserver> registered = new ArrayList<>(List.of(event -> { assertThat(event).isEqualTo(raised()); throw failure; },
				event -> { assertThat(event).isEqualTo(raised()); count.incrementAndGet(); }));
		var subject = new IncidentEventSubject(registered);
		registered.clear();
		assertThat(subject.notifyObservers(raised())).containsExactly(failure);
		assertThat(count.get()).isEqualTo(1);
		assertThat(new IncidentEventSubject(List.of()).notifyObservers(raised())).isEmpty();
	}

	@Test void eventIdentityRequiresValidFactsAndNormalizesTimeForStableReplay() {
		assertThat(raised().occurredAt()).isEqualTo(OffsetDateTime.parse("2026-10-07T16:00:00.123456+08:00"));
		assertThatThrownBy(() -> new FamilyAlertEvent(null, FamilyAlertEvent.Type.INCIDENT_RAISED, 601L, 101L, at)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new FamilyAlertEvent(id, null, 601L, 101L, at)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new FamilyAlertEvent(id, FamilyAlertEvent.Type.INCIDENT_RAISED, 601L, 101L, null)).isInstanceOf(NullPointerException.class);
		for (Long invalid : new Long[] {null, 0L, -1L}) {
			assertThatThrownBy(() -> new FamilyAlertEvent(id, FamilyAlertEvent.Type.INCIDENT_RAISED, invalid, 101L, at)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new FamilyAlertEvent(id, FamilyAlertEvent.Type.INCIDENT_RAISED, 601L, invalid, at)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test void messagesUseOnlySafeFactsAndAnIndependentConfiguredWindow() {
		var now = LocalDateTime.of(2026, 10, 7, 16, 0);
		var incident = Incident.createElderSos(101L, 15L, null, null, "Private location", "Private description", now);
		var notice = FamilyUrgentNotice.forIncident(raised(), incident, now, Duration.ofMinutes(45));
		assertThat(notice.title()).contains("HIGH");
		assertThat(notice.body()).contains("SOS").doesNotContain("Private");
		assertThat(notice.acknowledgeBy()).isEqualTo(now.plusMinutes(45));
		var unresolved = new FamilyAlertEvent(id, FamilyAlertEvent.Type.INCIDENT_UNRESOLVED, 601L, 101L, at);
		assertThat(FamilyUrgentNotice.forIncident(unresolved, incident, now, Duration.ofHours(2)).title()).contains("not taken up");
		assertThatThrownBy(() -> FamilyUrgentNotice.forIncident(raised(), incident, now, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FamilyUrgentNotice.forIncident(raised(), incident, now, Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
	}
}
