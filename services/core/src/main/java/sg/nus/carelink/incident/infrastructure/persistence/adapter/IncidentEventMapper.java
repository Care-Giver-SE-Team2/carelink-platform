package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import java.time.LocalDateTime;

import sg.nus.carelink.eventtypes.IncidentRaised;
import sg.nus.carelink.eventtypes.IncidentUpdated;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.eventtypes.SpotCheckUpdated;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.model.SpotCheck;

/**
 * incident's records as the events of {@code docs/platform/event-catalogue.md}. The adapters
 * publish them where the records are saved, in the same transaction: every change to an
 * incident, its timeline or a spot check goes through a save, so no code path, today's or a
 * later one, can change a record without its event.
 */
final class IncidentEventMapper {

	private IncidentEventMapper() {
	}

	static IncidentRaised raised(Incident incident) {
		return new IncidentRaised(incident.id(), incident.elderId(), incident.visitId(), incident.source().name(),
				incident.category().name(), incident.severity().name(), incident.status().name(),
				incident.description(), SingaporeTime.of(incident.reportedAt()));
	}

	/** One timeline entry, with the incident's state as it stands when the entry is written. */
	static IncidentUpdated updated(IncidentLog entry, Incident incident) {
		return new IncidentUpdated(entry.incidentId(), incident.elderId(), entry.id(), entry.action(), entry.actor(),
				entry.detail(), SingaporeTime.of(entry.occurredAt()), incident.status().name(),
				incident.severity().name(), SingaporeTime.of(incident.resolvedAt()));
	}

	static SpotCheckUpdated spotCheck(SpotCheck check, LocalDateTime changedAt) {
		return new SpotCheckUpdated(check.id(), check.elderId(), check.visitId(), check.caregiverId(),
				SingaporeTime.of(check.proposedTime()), name(check.approvalStatus()), name(check.result()),
				name(check.outcome()), check.finding(), check.caregiverResponse(), SingaporeTime.of(check.checkedAt()),
				SingaporeTime.of(changedAt));
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

}
