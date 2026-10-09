package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReviewFact;
import sg.nus.carelink.report.domain.model.RosterChangeFact;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.domain.model.ValueAddedFact;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;

/**
 * ReportFacts to the JSON in report_basis.facts: everything a generation run read, before any
 * reader's filter.
 *
 * <p>A stored document, written once and kept, so its shape is written down here in records of
 * its own rather than being whatever Jackson makes of the domain types - the same reasoning as
 * {@link ReportContentJson}. Renaming a field of a fact changes nothing already on disk; the
 * mapping below is where that change has to be faced, and {@link #VERSION} is raised with it.
 *
 * <p>One way only. The application reads a basis's numbers from their columns and never parses
 * this back; it is kept so the back office can show what a report was counted from. Times are
 * ISO-8601 text in the institution's local time, as the columns they came from hold them.
 */
final class ReportBasisJson {

	/** report_basis.facts_version for documents of this shape. */
	static final short VERSION = 1;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ReportBasisJson() {
	}

	static String write(ReportFacts facts) {
		return JSON.writeValueAsString(Stored.of(facts));
	}

	private static String text(Temporal moment) {
		return moment == null ? null : moment.toString();
	}

	/** The stored shape of one period's facts. */
	record Stored(
			Long elderId,
			String periodStart,
			String periodEnd,
			StoredElder elder,
			List<StoredVisit> visits,
			List<StoredVital> vitals,
			List<StoredObservation> observations,
			List<StoredIncident> incidents,
			List<StoredConfirmation> confirmations,
			List<StoredReview> reviews,
			List<StoredSpotCheck> spotChecks,
			List<StoredRosterChange> rosterChanges,
			List<StoredValueAdded> valueAdded) {

		static Stored of(ReportFacts facts) {
			return new Stored(
					facts.elderId(),
					text(facts.period().start()),
					text(facts.period().end()),
					StoredElder.of(facts.elder()),
					facts.visits().stream().map(StoredVisit::of).toList(),
					facts.vitals().stream().map(StoredVital::of).toList(),
					facts.observations().stream().map(StoredObservation::of).toList(),
					facts.incidents().stream().map(StoredIncident::of).toList(),
					facts.quality().confirmations().stream().map(StoredConfirmation::of).toList(),
					facts.quality().reviews().stream().map(StoredReview::of).toList(),
					facts.quality().spotChecks().stream().map(StoredSpotCheck::of).toList(),
					facts.changes().rosterChanges().stream().map(StoredRosterChange::of).toList(),
					facts.changes().valueAdded().stream().map(StoredValueAdded::of).toList());
		}
	}

	record StoredElder(String fullName, String gender, String dateOfBirth, String mobilityLevel, Boolean livesAlone,
			String medicalNotes, Long primaryCaregiverId, String primaryCaregiverName, Integer planVersion,
			BigDecimal planWeeklyHours) {

		static StoredElder of(ElderProfile e) {
			return new StoredElder(e.fullName(), e.gender(), text(e.dateOfBirth()), e.mobilityLevel(), e.livesAlone(),
					e.medicalNotes(), e.primaryCaregiverId(), e.primaryCaregiverName(), e.planVersion(), e.planWeeklyHours());
		}
	}

	record StoredVisit(Long id, Long caregiverId, String caregiverName, String serviceType, String scheduledStart,
			String status, int evidenceCount, int verifiedEvidenceCount, boolean skippedByFamily, Integer plannedMinutes,
			Integer workedMinutes) {

		static StoredVisit of(VisitFact v) {
			return new StoredVisit(v.id(), v.caregiverId(), v.caregiverName(), v.serviceType(), text(v.scheduledStart()),
					v.status().name(), v.evidenceCount(), v.verifiedEvidenceCount(), v.skippedByFamily(), v.plannedMinutes(),
					v.workedMinutes());
		}
	}

	record StoredVital(Long visitId, String metric, BigDecimal value, String unit, boolean outOfRange, String recordedAt) {

		static StoredVital of(VitalFact v) {
			return new StoredVital(v.visitId(), v.metric(), v.value(), v.unit(), v.outOfRange(), text(v.recordedAt()));
		}
	}

	record StoredObservation(Long visitId, String task, String note) {

		static StoredObservation of(ObservationFact o) {
			return new StoredObservation(o.visitId(), o.task(), o.note());
		}
	}

	record StoredIncident(Long id, String category, String severity, String status, String description,
			String reportedAt, String resolvedAt, List<StoredStep> timeline) {

		static StoredIncident of(IncidentFact i) {
			return new StoredIncident(i.id(), i.category(), i.severity(), i.status(), i.description(),
					text(i.reportedAt()), text(i.resolvedAt()), i.timeline().stream().map(StoredStep::of).toList());
		}
	}

	record StoredStep(String actor, String action, String detail, String occurredAt) {

		static StoredStep of(IncidentFact.Step s) {
			return new StoredStep(s.actor(), s.action(), s.detail(), text(s.occurredAt()));
		}
	}

	record StoredConfirmation(Long visitId, boolean disputed, Integer rating, String comment, String confirmedAt) {

		static StoredConfirmation of(ConfirmationFact c) {
			return new StoredConfirmation(c.visitId(), c.disputed(), c.rating(), c.comment(), text(c.confirmedAt()));
		}
	}

	record StoredReview(Long caregiverId, String caregiverName, String periodStart, String periodEnd, int overallRating,
			Integer punctualityScore, Integer careQualityScore, String notes, String renewalDecision) {

		static StoredReview of(ReviewFact r) {
			return new StoredReview(r.caregiverId(), r.caregiverName(), text(r.periodStart()), text(r.periodEnd()),
					r.overallRating(), r.punctualityScore(), r.careQualityScore(), r.notes(), r.renewalDecision());
		}
	}

	record StoredSpotCheck(Long id, Long caregiverId, String caregiverName, String proposedTime, String approvalStatus,
			String result, String outcome, String finding, String caregiverResponse, String checkedAt) {

		static StoredSpotCheck of(SpotCheckFact s) {
			return new StoredSpotCheck(s.id(), s.caregiverId(), s.caregiverName(), text(s.proposedTime()), s.approvalStatus(),
					s.result(), s.outcome(), s.finding(), s.caregiverResponse(), text(s.checkedAt()));
		}
	}

	record StoredRosterChange(Long visitId, String visitStart, Long originalCaregiverId, String originalCaregiverName,
			String status, String outcome, String decidedBy, Long assignedCaregiverId, String assignedCaregiverName) {

		static StoredRosterChange of(RosterChangeFact r) {
			return new StoredRosterChange(r.visitId(), text(r.visitStart()), r.originalCaregiverId(),
					r.originalCaregiverName(), r.status(), r.outcome(), r.decidedBy(), r.assignedCaregiverId(),
					r.assignedCaregiverName());
		}
	}

	record StoredValueAdded(Long id, String service, String requestedFor, String status, Long visitId) {

		static StoredValueAdded of(ValueAddedFact v) {
			return new StoredValueAdded(v.id(), v.service(), text(v.requestedFor()), v.status(), v.visitId());
		}
	}
}
