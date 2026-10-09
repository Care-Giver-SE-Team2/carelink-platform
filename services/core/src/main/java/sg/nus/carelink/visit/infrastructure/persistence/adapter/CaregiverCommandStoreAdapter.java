package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.model.CaregiverCommandReceipt;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;

@Repository
class CaregiverCommandStoreAdapter implements CaregiverCommandStore {
    private final JdbcTemplate jdbc;
    CaregiverCommandStoreAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<CaregiverCommandReceipt> find(Long actor, UUID key) {
        // Current read: a waiting command must see the receipt committed by its predecessor.
        return jdbc.query("select * from caregiver_command_receipt where actor_user_id=? and client_request_id=? for update",
                (rs, n) -> new CaregiverCommandReceipt(rs.getLong("actor_user_id"), UUID.fromString(rs.getString("client_request_id")),
                    rs.getString("action_code"), rs.getLong("visit_id"), rs.getString("payload_hash"), rs.getLong("result_id"),
                    rs.getInt("visit_version"), rs.getTimestamp("occurred_at").toLocalDateTime()), actor, key.toString()).stream().findFirst();
    }
    public Optional<UUID> incidentKey(Long actor, Long incidentId) {
        return jdbc.query("select client_request_id from caregiver_command_receipt where actor_user_id=? and action_code='REPORT_INCIDENT' and result_id=?",
                (rs, n) -> UUID.fromString(rs.getString(1)), actor, incidentId).stream().findFirst();
    }
    public void save(CaregiverCommandReceipt r) {
        jdbc.update("insert into caregiver_command_receipt(actor_user_id,client_request_id,action_code,visit_id,payload_hash,result_id,visit_version,occurred_at) values (?,?,?,?,?,?,?,?)",
                r.actorUserId(), r.clientRequestId().toString(), r.action(), r.visitId(), r.payloadHash(), r.resultId(), r.version(), r.occurredAt());
    }
    public void audit(Long actor, Long visit, String action, String result, String reason) {
        jdbc.update("insert into audit_log(actor_user_id,action,resource_type,resource_id,result,detail) values (?,?,'VISIT_EXECUTION',?,?,?)",
                actor, action, visit, result, reason);
    }
}
