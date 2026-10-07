package sg.nus.carelink.report.infrastructure.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
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

		assertThat(ReportFactsJdbcSource.visit(rs)).isEqualTo(new VisitFact(
				11L, 3L, "Daniel Goh", "Personal care", NINE, VisitFact.Status.VERIFIED, 2, 1));
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
}
