package sg.nus.carelink.rostering.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * Which visits an absence vacates that nobody has re-rostered yet: the absent caregiver's
 * visits on the days they are away that have not started, are still ahead, and have no change.
 * Shared by the re-rostering and by the screens that show what is left to do, so both count
 * the same thing.
 */
final class Vacancies {

	private Vacancies() {
	}

	static List<VisitReassignment.VisitSlot> notYetRerostered(VisitReassignment visits, AbsenceReport absence,
			Set<Long> handledVisitIds, LocalDateTime now) {
		if (!absence.isApproved()) {
			return List.of();
		}
		LocalDateTime from = absence.windowStart().isAfter(now) ? absence.windowStart() : now;
		if (!from.isBefore(absence.windowEnd())) {
			return List.of();
		}
		return visits.unstartedFor(absence.caregiverId(), from, absence.windowEnd()).stream()
				.filter(visit -> visit.start().isAfter(now))
				.filter(visit -> !handledVisitIds.contains(visit.visitId()))
				.toList();
	}
}
