package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.profile.domain.model.*;
import sg.nus.carelink.profile.domain.repository.CredentialSubmissionStore;

@Repository
class CredentialSubmissionStoreAdapter implements CredentialSubmissionStore {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    CredentialSubmissionStoreAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void lockCaregiver(Long caregiverId, LocalDateTime now) {
        jdbc.update("insert into caregiver_credential_submission_guard(caregiver_id,created_at) values (?,?) "
                + "on duplicate key update caregiver_id=caregiver_id", caregiverId, Timestamp.valueOf(now));
        jdbc.queryForObject("select caregiver_id from caregiver_credential_submission_guard where caregiver_id=? for update",
                Long.class, caregiverId);
    }

    @Override public List<CredentialType> types() {
        return jdbc.query("select id,name,created_at from credential_type order by name,id", (rs, n) ->
                new CredentialType(rs.getLong("id"), rs.getString("name"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Override public Optional<Receipt> receipt(Long actorId, String requestId) {
        return jdbc.query("select payload_sha256,result_snapshot from credential_submission where submitted_by_user_id=? and client_request_id=?",
                (rs, n) -> new Receipt(rs.getString(1), JSON.readValue(rs.getString(2), Result.class)), actorId, requestId)
                .stream().findFirst();
    }

    @Override public Long insertCredential(Credential c) {
        return insert("insert into credential(caregiver_id,credential_type_id,certificate_no,issuing_body,valid_from,expiry_date,status,created_at,updated_at,renews_credential_id) "
                + "values (?,?,?,?,?,?,'SUBMITTED',?,?,?)", c.caregiverId(), c.credentialTypeId(), c.certificateNo(),
                c.issuingBody(), c.validFrom(), c.expiryDate(), c.createdAt(), c.updatedAt(), c.renewsCredentialId());
    }

    @Override public Long insertDocument(CredentialDocument d) {
        return insert("insert into credential_document(credential_id,storage_key,original_filename,content_type,byte_size,sha256,uploaded_by_user_id,uploaded_at,display_order) "
                + "values (?,?,?,?,?,?,?,?,?)", d.credentialId(), d.storageKey(), d.fileName(), d.contentType(),
                d.byteSize(), d.sha256(), d.uploadedByUserId(), d.uploadedAt(), d.displayOrder());
    }

    private Long insert(String sql, Object... values) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }

    @Override public void saveSubmission(Long actorId, String requestId, String action, Long resubmitsId,
            String hash, Result result) {
        jdbc.update("insert into credential_submission(credential_id,submitted_by_user_id,client_request_id,action,resubmits_credential_id,payload_sha256,submitted_at,result_snapshot) values (?,?,?,?,?,?,?,?)",
                result.credentialId(), actorId, requestId, action, resubmitsId, hash, result.submittedAt(), JSON.writeValueAsString(result));
    }

    @Override public Map<Long, Long> resubmissions(Long caregiverId) {
        Map<Long, Long> result = new HashMap<>();
        jdbc.query("select s.credential_id,s.resubmits_credential_id from credential_submission s join credential c on c.id=s.credential_id "
                + "where c.caregiver_id=? and s.resubmits_credential_id is not null", rs -> {
                    result.put(rs.getLong(1), rs.getLong(2));
                }, caregiverId);
        return Map.copyOf(result);
    }

    @Override public List<CredentialDocument> documents(Long id) {
        return jdbc.query("select * from credential_document where credential_id=? order by display_order,id", (rs, n) ->
                new CredentialDocument(rs.getLong("id"), rs.getLong("credential_id"), rs.getString("storage_key"),
                        rs.getString("original_filename"), rs.getString("content_type"), rs.getLong("byte_size"),
                        rs.getString("sha256"), rs.getLong("uploaded_by_user_id"), rs.getTimestamp("uploaded_at").toLocalDateTime(),
                        rs.getInt("display_order")), id);
    }

    @Override public void audit(Long actorId, String action, Long id, LocalDateTime now) {
        jdbc.update("insert into audit_log(actor_user_id,action,resource_type,resource_id,result,occurred_at) values (?,?,'CREDENTIAL',?,'OK',?)",
                actorId, action, id, now);
    }
}
