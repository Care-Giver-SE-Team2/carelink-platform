package sg.nus.carelink.report.infrastructure.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.coreapi.CoreApi;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReviewFact;
import sg.nus.carelink.report.domain.model.RosterChangeFact;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.domain.model.ValueAddedFact;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;

/**
 * Column mapping only. The SQL and the time zone path need a real MySQL and are exercised by
 * {@code ReportFlowIT}; what can be checked without one is that each row becomes the right
 * record - an unassigned visit keeps a null caregiver rather than caregiver #0, times are read
 * through {@code getTimestamp}, the way they were written, and names come from core.
 */
class KeptFactsSourceTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 9, 14, 9, 0);

	private final CoreApi core = mock(CoreApi.class);
	private final KeptFactsSource.CaregiverNames names = new KeptFactsSource.CaregiverNames(core);

	@BeforeEach
	void coreKnowsDanielAndMeiLing() {
		when(core.findCaregiverPublicProfile(3L))
				.thenReturn(Optional.of(new CoreApi.CaregiverPublicProfile(3L, "Daniel Goh", List.of())));
		when(core.findCaregiverPublicProfile(5L))
				.thenReturn(Optional.of(new CoreApi.CaregiverPublicProfile(5L, "Mei Ling", List.of())));
	}

	@Test
	void aCaregiversNameIsAskedOfCoreOnceAndOneCoreNoLongerHasHasNone() {
		assertThat(names.of(3L)).isEqualTo("Daniel Goh");
		assertThat(names.of(3L)).isEqualTo("Daniel Goh");
		assertThat(names.of(9L)).isNull();
		assertThat(names.of(null)).isNull();

		verify(core, times(1)).findCaregiverPublicProfile(3L);
	}

	@Test
	void aBoundaryIsHandedToTheDriverAsATimestampOfTheSameWallClock() {
		Timestamp boundary = KeptFactsSource.boundary(NINE);

		assertThat(boundary.toLocalDateTime()).isEqualTo(NINE);
	}

	@Test
	void aVisitRowBecomesAVisitFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(11L);
		when(rs.getLong("caregiver_id")).thenReturn(3L);
		when(rs.wasNull()).thenReturn(false);
		when(rs.getString("service_type")).thenReturn("Personal care");
		when(rs.getTimestamp("scheduled_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("status")).thenReturn("VERIFIED");
		when(rs.getInt("evidence_count")).thenReturn(2);
		when(rs.getInt("verified_evidence_count")).thenReturn(1);
		when(rs.getInt("planned_minutes")).thenReturn(60);
		when(rs.getInt("worked_minutes")).thenReturn(55);

		assertThat(KeptFactsSource.visit(rs, names)).isEqualTo(new VisitFact(
				11L, 3L, "Daniel Goh", "Personal care", NINE, VisitFact.Status.VERIFIED, 2, 1, false, 60, 55));
	}

	@Test
	void aVisitTheFamilySkippedIsReadAsSuch() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(12L);
		when(rs.getLong("caregiver_id")).thenReturn(3L);
		when(rs.getTimestamp("scheduled_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("status")).thenReturn("CANCELLED");
		when(rs.getBoolean("skipped_by_family")).thenReturn(true);

		assertThat(KeptFactsSource.visit(rs, names).cancelledByFamily()).isTrue();
	}

	@Test
	void anUnassignedVisitHasNoCaregiverRatherThanCaregiverZero() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(13L);
		when(rs.getLong("caregiver_id")).thenReturn(0L);
		when(rs.wasNull()).thenReturn(true);
		when(rs.getTimestamp("scheduled_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("status")).thenReturn("SCHEDULED");

		VisitFact visit = KeptFactsSource.visit(rs, names);

		assertThat(visit.caregiverId()).isNull();
		assertThat(visit.hasCaregiver()).isFalse();
		assertThat(visit.plannedMinutes()).as("no end scheduled").isNull();
		assertThat(visit.workedMinutes()).as("never checked in").isNull();
	}

	@Test
	void aReadingRowBecomesAVitalFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("visit_id")).thenReturn(12L);
		when(rs.getString("metric")).thenReturn("systolic");
		when(rs.getBigDecimal("value")).thenReturn(new BigDecimal("142.00"));
		when(rs.getString("unit")).thenReturn("mmHg");
		when(rs.getBoolean("out_of_range")).thenReturn(true);
		when(rs.getTimestamp("recorded_at")).thenReturn(Timestamp.valueOf(NINE));

		assertThat(KeptFactsSource.vital(rs)).isEqualTo(
				new VitalFact(12L, "systolic", new BigDecimal("142.00"), "mmHg", true, NINE));
	}

	@Test
	void aNoteIsTrimmedOfTheSpaceAFormLeavesAroundIt() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("visit_id")).thenReturn(11L);
		when(rs.getString("name")).thenReturn("Mobility");
		when(rs.getString("caregiver_note")).thenReturn("  Walked to the void deck.\n");

		assertThat(KeptFactsSource.observation(rs))
				.isEqualTo(new ObservationFact(11L, "Mobility", "Walked to the void deck."));
	}

	@Test
	void anOpenIncidentRowHasNoResolutionTime() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(5L);
		when(rs.getString("category")).thenReturn("SOS");
		when(rs.getString("severity")).thenReturn("HIGH");
		when(rs.getString("status")).thenReturn("OPEN");
		when(rs.getTimestamp("reported_at")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getTimestamp("resolved_at")).thenReturn(null);

		IncidentFact incident = KeptFactsSource.incident(rs);

		assertThat(incident.reportedAt()).isEqualTo(NINE);
		assertThat(incident.resolvedAt()).isNull();
		assertThat(incident.timeline()).isEmpty();
	}

	@Test
	void aTimelineRowBecomesAStep() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("actor")).thenReturn("Ben Lim (demo-ben)");
		when(rs.getString("action")).thenReturn("CLAIMED");
		when(rs.getString("detail")).thenReturn("taken over; countdown stopped");
		when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.valueOf(NINE));

		assertThat(KeptFactsSource.step(rs)).isEqualTo(
				new IncidentFact.Step("Ben Lim (demo-ben)", "CLAIMED", "taken over; countdown stopped", NINE));
	}

	@Test
	void coresProfileBecomesTheElderWithThePlanAndThePrimaryCaregiver() {
		CoreApi.ElderReportProfile fromCore = new CoreApi.ElderReportProfile(1L, "Tan Ah Mei", "FEMALE",
				LocalDate.of(1941, 3, 2), "ASSISTIVE_CANE", true, "Hypertension.", 3L, "Daniel Goh", 3,
				new BigDecimal("6.50"));

		assertThat(KeptFactsSource.profile(fromCore)).isEqualTo(new ElderProfile("Tan Ah Mei", "FEMALE",
				LocalDate.of(1941, 3, 2), "ASSISTIVE_CANE", true, "Hypertension.", 3L, "Daniel Goh", 3, new BigDecimal("6.50")));
	}

	@Test
	void aProfileWithNothingOnRecordKeepsItsNullsRatherThanZerosAndFalse() {
		ElderProfile profile = KeptFactsSource.profile(new CoreApi.ElderReportProfile(1L, "Tan Ah Mei", null, null,
				null, null, null, null, null, null, null));

		assertThat(profile.livesAlone()).isNull();
		assertThat(profile.primaryCaregiverId()).isNull();
		assertThat(profile.planVersion()).isNull();
		assertThat(profile.hasPlan()).isFalse();
	}

	@Test
	void anAnswerRowBecomesAConfirmationAndAnUnratedOneHasNoRating() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("visit_id")).thenReturn(12L);
		when(rs.getString("confirmation_status")).thenReturn("DISPUTED");
		when(rs.getInt("rating")).thenReturn(0);
		when(rs.wasNull()).thenReturn(true);
		when(rs.getString("comment")).thenReturn("He left early.");
		when(rs.getTimestamp("confirmed_at")).thenReturn(Timestamp.valueOf(NINE));

		assertThat(KeptFactsSource.confirmation(rs))
				.isEqualTo(new ConfirmationFact(12L, true, null, "He left early.", NINE));
	}

	@Test
	void aReviewRowBecomesAReviewFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("caregiver_id")).thenReturn(3L);
		when(rs.getObject("period_start", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 14));
		when(rs.getObject("period_end", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 20));
		when(rs.getInt("overall_rating")).thenReturn(4);
		when(rs.getInt("punctuality_score")).thenReturn(5);
		when(rs.getInt("care_quality_score")).thenReturn(4);
		when(rs.getString("feedback_notes")).thenReturn("Kind.");
		when(rs.getString("renewal_decision")).thenReturn("RENEW_CURRENT");

		assertThat(KeptFactsSource.review(rs, names)).isEqualTo(new ReviewFact(3L, "Daniel Goh",
				LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 4, 5, 4, "Kind.", "RENEW_CURRENT"));
	}

	@Test
	void aSpotCheckRowBecomesASpotCheckFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(4L);
		when(rs.getLong("caregiver_id")).thenReturn(3L);
		when(rs.getTimestamp("proposed_time")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("approval_status")).thenReturn("APPROVED");
		when(rs.getString("result")).thenReturn("MEETS_STANDARD");
		when(rs.getString("outcome")).thenReturn("COMPLETED");
		when(rs.getString("finding")).thenReturn("All in order.");
		when(rs.getString("caregiver_response")).thenReturn(null);
		when(rs.getTimestamp("checked_at")).thenReturn(Timestamp.valueOf(NINE.plusMinutes(40)));

		assertThat(KeptFactsSource.spotCheck(rs, names)).isEqualTo(new SpotCheckFact(4L, 3L, "Daniel Goh", NINE,
				"APPROVED", "MEETS_STANDARD", "COMPLETED", "All in order.", null, NINE.plusMinutes(40)));
	}

	@Test
	void aRosterChangeRowWithoutAReplacementHasNone() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("visit_id")).thenReturn(13L);
		when(rs.getTimestamp("visit_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getLong("original_caregiver_id")).thenReturn(3L);
		when(rs.getString("status")).thenReturn("AWAITING_FAMILY");
		// the original caregiver is there; the replacement is not
		when(rs.wasNull()).thenReturn(false, true);

		RosterChangeFact change = KeptFactsSource.rosterChange(rs, names);

		assertThat(change).isEqualTo(new RosterChangeFact(13L, NINE, 3L, "Daniel Goh", "AWAITING_FAMILY",
				null, null, null, null));
		assertThat(change.hasReplacement()).isFalse();
	}

	@Test
	void aReplacementIsNamedByCore() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("visit_id")).thenReturn(13L);
		when(rs.getTimestamp("visit_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getLong("original_caregiver_id")).thenReturn(3L);
		when(rs.getLong("assigned_caregiver_id")).thenReturn(5L);
		when(rs.getString("status")).thenReturn("RESOLVED");
		when(rs.getString("outcome")).thenReturn("REPLACED");
		when(rs.getString("decided_by")).thenReturn("FAMILY");

		assertThat(KeptFactsSource.rosterChange(rs, names)).isEqualTo(new RosterChangeFact(13L, NINE, 3L, "Daniel Goh",
				"RESOLVED", "REPLACED", "FAMILY", 5L, "Mei Ling"));
	}

	@Test
	void aValueAddedRowBecomesAValueAddedFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(7L);
		when(rs.getString("service")).thenReturn("Hospital escort");
		when(rs.getTimestamp("requested_schedule")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("status")).thenReturn("DISPATCHED");
		when(rs.getLong("visit_id")).thenReturn(15L);

		assertThat(KeptFactsSource.valueAdded(rs))
				.isEqualTo(new ValueAddedFact(7L, "Hospital escort", NINE, "DISPATCHED", 15L));
	}
}
