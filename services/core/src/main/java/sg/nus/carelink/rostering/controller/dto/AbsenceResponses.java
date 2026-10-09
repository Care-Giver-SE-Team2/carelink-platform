package sg.nus.carelink.rostering.controller.dto;

import java.time.LocalDate;

import sg.nus.carelink.rostering.application.AbsenceQueryService;
import sg.nus.carelink.rostering.application.AbsenceReRosteringService;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;

/** Response bodies that are not simply an application view. */
public final class AbsenceResponses {

	private AbsenceResponses() {
	}

	/** A caregiver's own absence: what they asked for and where it stands, nothing about anybody else. */
	public record OwnAbsence(Long id, AbsenceReport.Type type, LocalDate startDate, LocalDate endDate, String reason,
			AbsenceReport.Status status) {

		public static OwnAbsence of(AbsenceReport absence) {
			return new OwnAbsence(absence.id(), absence.type(), absence.startDate(), absence.endDate(), absence.reason(),
					absence.status());
		}
	}

	/** What one re-roster did, and the absence as it now stands. */
	public record ReRostered(AbsenceReRosteringService.ReRosterOutcome outcome, AbsenceQueryService.AbsenceCase absence) {
	}
}
