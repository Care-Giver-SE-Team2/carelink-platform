package sg.nus.carelink.profile.infrastructure.lookup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sg.nus.carelink.profile.application.ElderReportProfiles;

/**
 * {@link ElderReportProfiles} with the statement report's fact source runs today, so a report
 * written from this call says what it said before. The plan is the latest published one, the
 * one in force when the report is written.
 */
@Component
class JdbcElderReportProfiles implements ElderReportProfiles {

	private static final String PROFILE = """
			select e.id, e.full_name, e.gender, e.date_of_birth, e.mobility_level, e.lives_alone, e.medical_notes,
			       pc.caregiver_id as primary_caregiver_id, c.full_name as primary_caregiver_name,
			       p.version as plan_version, p.total_hours as plan_hours
			from elder e
			left join elder_primary_caregiver pc on pc.elder_id = e.id
			left join caregiver c on c.id = pc.caregiver_id
			left join care_plan p on p.id = (
			    select cp.id from care_plan cp
			    where cp.elder_id = e.id and cp.status = 'PUBLISHED'
			    order by cp.version desc, cp.id desc
			    limit 1)
			where e.id = :elderId
			""";

	private final JdbcClient jdbc;

	JdbcElderReportProfiles(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Profile> find(Long elderId) {
		return jdbc.sql(PROFILE).param("elderId", elderId).query(JdbcElderReportProfiles::profile).optional();
	}

	private static Profile profile(ResultSet rs, int row) throws SQLException {
		boolean livesAlone = rs.getBoolean("lives_alone");
		Boolean known = rs.wasNull() ? null : livesAlone;
		long caregiverId = rs.getLong("primary_caregiver_id");
		Long caregiver = rs.wasNull() ? null : caregiverId;
		int version = rs.getInt("plan_version");
		Integer planVersion = rs.wasNull() ? null : version;
		return new Profile(rs.getLong("id"), rs.getString("full_name"), rs.getString("gender"),
				rs.getObject("date_of_birth", LocalDate.class), rs.getString("mobility_level"), known,
				rs.getString("medical_notes"), caregiver, rs.getString("primary_caregiver_name"), planVersion,
				rs.getBigDecimal("plan_hours"));
	}

}
