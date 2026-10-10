package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.time.LocalDateTime;

import sg.nus.carelink.eventtypes.RosterChangeUpdated;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.rostering.domain.model.RosterChange;

/**
 * A roster change as the {@code RosterChangeUpdated} event of {@code docs/platform/event-catalogue.md}.
 * The adapter publishes it on every save, in the same transaction: re-rostering, the scan that
 * applies a default plan, a manager's choice and a leave cover all save through it.
 */
final class RosterChangeEventMapper {

	private RosterChangeEventMapper() {
	}

	static RosterChangeUpdated updated(RosterChange change, LocalDateTime changedAt) {
		return new RosterChangeUpdated(change.id(), change.visitId(), change.elderId(), change.absenceId(),
				SingaporeTime.of(change.visitStart()), change.originalCaregiverId(), name(change.status()),
				name(change.outcome()), name(change.decidedBy()), change.assignedCaregiverId(),
				SingaporeTime.of(changedAt));
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

}
