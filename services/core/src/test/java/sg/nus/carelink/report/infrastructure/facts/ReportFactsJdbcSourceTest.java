package sg.nus.carelink.report.infrastructure.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

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
 * record - an unassigned visit keeps a null caregiver rather than caregiver #0, and times are
 * read through {@code getTimestamp}, the way Hibernate reads the columns it wrote.
 */
class ReportFactsJdbcSourceTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 9, 14, 9, 0);

	@Test
	void aBoundaryIsHandedToTheDriverAsATimestampOfTheSameWallClock() {
		Timestamp boundary = ReportFactsJdbcSource.boundary(NINE);

		assertThat(boundary.toLocalDateTime()).isEqualTo(NINE);
	}

	@Test
	void aVisitRowBecomesAVisitFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(11L);
		when(rs.getLong("caregiver_id")).thenReturn(3L);
		when(rs.wasNull()).thenReturn(false);
		when(rs.getString("caregiver_name")).thenReturn("Daniel Goh");
		when(rs.getString("service_type")).thenReturn("Personal care");
		when(rs.getTimestamp("scheduled_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("status")).thenReturn("VERIFIED");
		when(rs.getInt("evidence_count")).thenReturn(2);
		when(rs.getInt("verified_evidence_count")).thenReturn(1);
		when(rs.getInt("planned_minutes")).thenReturn(60);
		when(rs.getInt("worked_minutes")).thenReturn(55);

		assertThat(ReportFactsJdbcSource.visit(rs)).isEqualTo(new VisitFact(
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

		assertThat(ReportFactsJdbcSource.visit(rs).cancelledByFamily()).isTrue();
	}

	@Test
	void anUnassignedVisitHasNoCaregiverRatherThanCaregiverZero() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(13L);
		when(rs.getLong("caregiver_id")).thenReturn(0L);
		when(rs.wasNull()).thenReturn(true);
		when(rs.getTimestamp("scheduled_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("status")).thenReturn("SCHEDULED");

		VisitFact visit = ReportFactsJdbcSource.visit(rs);

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

		assertThat(ReportFactsJdbcSource.vital(rs)).isEqualTo(
				new VitalFact(12L, "systolic", new BigDecimal("142.00"), "mmHg", true, NINE));
	}

	@Test
	void aNoteIsTrimmedOfTheSpaceAFormLeavesAroundIt() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("visit_id")).thenReturn(11L);
		when(rs.getString("name")).thenReturn("Mobility");
		when(rs.getString("caregiver_note")).thenReturn("  Walked to the void deck.\n");

		assertThat(ReportFactsJdbcSource.observation(rs))
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

		IncidentFact incident = ReportFactsJdbcSource.incident(rs);

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

		assertThat(ReportFactsJdbcSource.step(rs)).isEqualTo(
				new IncidentFact.Step("Ben Lim (demo-ben)", "CLAIMED", "taken over; countdown stopped", NINE));
	}

	@Test
	void aProfileRowBecomesTheElderWithThePlanAndThePrimaryCaregiver() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("full_name")).thenReturn("Tan Ah Mei");
		when(rs.getString("gender")).thenReturn("FEMALE");
		when(rs.getObject("date_of_birth", LocalDate.class)).thenReturn(LocalDate.of(1941, 3, 2));
		when(rs.getString("mobility_level")).thenReturn("ASSISTIVE_CANE");
		when(rs.getBoolean("lives_alone")).thenReturn(true);
		when(rs.getString("medical_notes")).thenReturn("Hypertension.");
		when(rs.getLong("primary_caregiver_id")).thenReturn(3L);
		when(rs.getString("primary_caregiver_name")).thenReturn("Daniel Goh");
		when(rs.getInt("plan_version")).thenReturn(3);
		when(rs.getBigDecimal("plan_hours")).thenReturn(new BigDecimal("6.50"));

		assertThat(ReportFactsJdbcSource.profile(rs)).isEqualTo(new ElderProfile("Tan Ah Mei", "FEMALE",
				LocalDate.of(1941, 3, 2), "ASSISTIVE_CANE", true, "Hypertension.", 3L, "Daniel Goh", 3, new BigDecimal("6.50")));
	}

	@Test
	void aProfileWithNothingOnRecordKeepsItsNullsRatherThanZerosAndFalse() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.wasNull()).thenReturn(true);

		ElderProfile profile = ReportFactsJdbcSource.profile(rs);

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

		assertThat(ReportFactsJdbcSource.confirmation(rs))
				.isEqualTo(new ConfirmationFact(12L, true, null, "He left early.", NINE));
	}

	@Test
	void aReviewRowBecomesAReviewFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("caregiver_id")).thenReturn(3L);
		when(rs.getString("caregiver_name")).thenReturn("Daniel Goh");
		when(rs.getObject("period_start", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 14));
		when(rs.getObject("period_end", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 20));
		when(rs.getInt("overall_rating")).thenReturn(4);
		when(rs.getInt("punctuality_score")).thenReturn(5);
		when(rs.getInt("care_quality_score")).thenReturn(4);
		when(rs.getString("feedback_notes")).thenReturn("Kind.");
		when(rs.getString("renewal_decision")).thenReturn("RENEW_CURRENT");

		assertThat(ReportFactsJdbcSource.review(rs)).isEqualTo(new ReviewFact(3L, "Daniel Goh",
				LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 4, 5, 4, "Kind.", "RENEW_CURRENT"));
	}

	@Test
	void aSpotCheckRowBecomesASpotCheckFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(4L);
		when(rs.getLong("caregiver_id")).thenReturn(3L);
		when(rs.getString("caregiver_name")).thenReturn("Daniel Goh");
		when(rs.getTimestamp("proposed_time")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("approval_status")).thenReturn("APPROVED");
		when(rs.getString("result")).thenReturn("MEETS_STANDARD");
		when(rs.getString("outcome")).thenReturn("COMPLETED");
		when(rs.getString("finding")).thenReturn("All in order.");
		when(rs.getString("caregiver_response")).thenReturn(null);
		when(rs.getTimestamp("checked_at")).thenReturn(Timestamp.valueOf(NINE.plusMinutes(40)));

		assertThat(ReportFactsJdbcSource.spotCheck(rs)).isEqualTo(new SpotCheckFact(4L, 3L, "Daniel Goh", NINE,
				"APPROVED", "MEETS_STANDARD", "COMPLETED", "All in order.", null, NINE.plusMinutes(40)));
	}

	@Test
	void aRosterChangeRowWithoutAReplacementHasNone() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("visit_id")).thenReturn(13L);
		when(rs.getTimestamp("visit_start")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getLong("original_caregiver_id")).thenReturn(3L);
		when(rs.getString("original_caregiver_name")).thenReturn("Daniel Goh");
		when(rs.getString("status")).thenReturn("AWAITING_FAMILY");
		when(rs.wasNull()).thenReturn(true);

		RosterChangeFact change = ReportFactsJdbcSource.rosterChange(rs);

		assertThat(change).isEqualTo(new RosterChangeFact(13L, NINE, 3L, "Daniel Goh", "AWAITING_FAMILY",
				null, null, null, null));
		assertThat(change.hasReplacement()).isFalse();
	}

	@Test
	void aValueAddedRowBecomesAValueAddedFact() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(7L);
		when(rs.getString("service")).thenReturn("Hospital escort");
		when(rs.getTimestamp("requested_schedule")).thenReturn(Timestamp.valueOf(NINE));
		when(rs.getString("status")).thenReturn("DISPATCHED");
		when(rs.getLong("visit_id")).thenReturn(15L);

		assertThat(ReportFactsJdbcSource.valueAdded(rs))
				.isEqualTo(new ValueAddedFact(7L, "Hospital escort", NINE, "DISPATCHED", 15L));
	}
}
