package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CaregiverApplication;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.*;
import sg.nus.carelink.profile.domain.repository.CaregiverAdmissionStore;
import sg.nus.carelink.shared.error.ResourceNotFound;

@Repository
class CaregiverAdmissionStoreAdapter implements CaregiverAdmissionStore {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    CaregiverAdmissionStoreAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Caregiver lockCaregiver(Long id) {
        // One lock order for self updates and manager decisions, including first use.
        var caregiver = jdbc.query("select * from caregiver where id=? for update", (rs, n) -> caregiver(rs), id)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFound("Caregiver", id));
        jdbc.update("insert into caregiver_admission_state(caregiver_id) values (?) on duplicate key update caregiver_id=caregiver_id", id);
        return caregiver;
    }
    @Override public long version(Long id) {
        return jdbc.query("select version from caregiver_admission_state where caregiver_id=?", (rs,n) -> rs.getLong(1), id)
                .stream().findFirst().orElse(0L);
    }
    @Override public void advance(Long id) { jdbc.update("update caregiver_admission_state set version=version+1 where caregiver_id=?", id); }
    @Override public Optional<CaregiverApplication> findByCaregiver(Long id) {
        return jdbc.query("select * from caregiver_application where caregiver_id=?", (rs,n) -> application(rs), id).stream().findFirst();
    }
    @Override public Optional<CaregiverApplication> findById(Long id) {
        return jdbc.query("select * from caregiver_application where id=?", (rs,n) -> application(rs), id).stream().findFirst();
    }
    @Override public List<CaregiverApplication> pending(int offset, int size) {
        return jdbc.query("select * from caregiver_application where status='PENDING' order by submitted_at desc,id desc limit ? offset ?",
                (rs,n) -> application(rs), size, offset);
    }
    @Override public long pendingCount() { return Objects.requireNonNull(jdbc.queryForObject("select count(*) from caregiver_application where status='PENDING'", Long.class)); }
    @Override public CaregiverApplication save(CaregiverApplication a) {
        if (a.id() == null) {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("insert into caregiver_application(caregiver_id,status,round_number,full_name,phone,declared_dialects,requested_sector,submitted_at) values (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1,a.caregiverId()); statement.setString(2,a.status().name()); statement.setInt(3,a.roundNumber());
                statement.setString(4,a.details().fullName()); statement.setString(5,a.details().phone());
                statement.setString(6,a.details().declaredDialects()); statement.setString(7,a.details().requestedSector());
                statement.setTimestamp(8,Timestamp.valueOf(a.submittedAt())); return statement;
            }, key);
            return findById(Objects.requireNonNull(key.getKey()).longValue()).orElseThrow();
        }
        jdbc.update("update caregiver_application set status=?,round_number=?,full_name=?,phone=?,declared_dialects=?,requested_sector=?,submitted_at=?,reviewed_by_user_id=?,reviewed_at=?,review_reason=? where id=?",
                a.status().name(), a.roundNumber(), a.details().fullName(), a.details().phone(), a.details().declaredDialects(), a.details().requestedSector(),
                timestamp(a.submittedAt()),a.reviewedByUserId(),timestamp(a.reviewedAt()),a.reviewReason(),a.id());
        return a;
    }
    @Override public void history(CaregiverApplication a, Long actor, String action, long version, LocalDateTime now) {
        jdbc.update("insert into caregiver_application_history(application_id,actor_user_id,action,version,snapshot,occurred_at) values (?,?,?,?,?,?)",
                a.id(),actor,action,version,JSON.writeValueAsString(a),timestamp(now));
        jdbc.update("insert into audit_log(actor_user_id,action,resource_type,resource_id,result,occurred_at) values (?,?,'CAREGIVER_APPLICATION',?,'OK',?)",
                actor,"APPLICATION_" + action,a.id(),timestamp(now));
    }
    @Override public List<History> history(Long id) {
        return jdbc.query("select * from caregiver_application_history where application_id=? order by id", (rs,n) ->
                new History(rs.getLong("actor_user_id"),rs.getString("action"),rs.getLong("version"),
                        JSON.readValue(rs.getString("snapshot"),CaregiverApplication.class),rs.getTimestamp("occurred_at").toLocalDateTime()),id);
    }
    @Override public Optional<Receipt> receipt(Long actor, String key) {
        return jdbc.query("select payload_sha256,result_snapshot from caregiver_admission_receipt where actor_user_id=? and client_request_id=?",
                (rs,n) -> new Receipt(rs.getString(1),JSON.readValue(rs.getString(2),View.class)),actor,key).stream().findFirst();
    }
    @Override public void receipt(Long actor, String key, String hash, View result) {
        jdbc.update("insert into caregiver_admission_receipt(actor_user_id,client_request_id,caregiver_id,payload_sha256,result_snapshot) values (?,?,?,?,?)",
                actor,key,result.caregiverId(),hash,JSON.writeValueAsString(result));
    }
    @Override public void approveProfile(Long id, Details details) {
        // Requested sector is an intention, never the manager's authoritative assignment.
        jdbc.update("update caregiver set status='AVAILABLE',full_name=?,phone=?,dialects=? where id=? and status='ONBOARDING'",
                details.fullName(),details.phone(),details.declaredDialects(),id);
    }
    @Override public void contact(Long id, String phone) { jdbc.update("update caregiver set phone=? where id=?",phone,id); }
    @Override public void auditContact(Long id, Long actor, LocalDateTime now) {
        jdbc.update("insert into audit_log(actor_user_id,action,resource_type,resource_id,result,occurred_at) values (?,'CONTACT_UPDATE','CAREGIVER',?,'OK',?)",
                actor,id,timestamp(now));
    }
    private static Timestamp timestamp(LocalDateTime time) { return time == null ? null : Timestamp.valueOf(time); }
    private static CaregiverApplication application(ResultSet rs) throws SQLException {
        var reviewed = rs.getTimestamp("reviewed_at");
        return new CaregiverApplication(rs.getLong("id"),rs.getLong("caregiver_id"),Status.valueOf(rs.getString("status")),rs.getInt("round_number"),
                new Details(rs.getString("full_name"),rs.getString("phone"),rs.getString("declared_dialects"),rs.getString("requested_sector")),
                rs.getTimestamp("submitted_at").toLocalDateTime(),rs.getObject("reviewed_by_user_id",Long.class),
                reviewed == null ? null : reviewed.toLocalDateTime(),rs.getString("review_reason"));
    }
    private static Caregiver caregiver(ResultSet rs) throws SQLException {
        return new Caregiver(rs.getLong("id"),rs.getLong("user_id"),rs.getString("full_name"),rs.getString("phone"),rs.getString("sector"),
                rs.getString("dialects"),Caregiver.Status.valueOf(rs.getString("status")),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
