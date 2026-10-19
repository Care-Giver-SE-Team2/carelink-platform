package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.model.*;
import sg.nus.carelink.visit.domain.repository.VisitEvidenceFileStore;

@Repository
class JdbcVisitEvidenceFileStore implements VisitEvidenceFileStore {
    private static final String SELECT="select e.*,f.evidence_id,f.uploader_user_id,f.storage_key,f.content_type,f.byte_size,f.sha256,f.original_sha256,f.sanitized_original_name,f.visit_task_id,f.submitted_at from visit_evidence e left join visit_evidence_file f on f.evidence_id=e.id where e.visit_id=?";
    private final JdbcTemplate jdbc;
    JdbcVisitEvidenceFileStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public List<Entry> list(Long visit,int offset,int size) { return jdbc.query(SELECT+" order by e.id desc limit ? offset ?",JdbcVisitEvidenceFileStore::entry,visit,size,offset); }
    @Override public Optional<Entry> find(Long visit,Long evidence) { return jdbc.query(SELECT+" and e.id=?",JdbcVisitEvidenceFileStore::entry,visit,evidence).stream().findFirst(); }
    @Override public long count(Long visit) { return Objects.requireNonNull(jdbc.queryForObject("select count(*) from visit_evidence where visit_id=?",Long.class,visit)); }
    @Override public long fileCount(Long visit) { return Objects.requireNonNull(jdbc.queryForObject("select count(*) from visit_evidence e join visit_evidence_file f on f.evidence_id=e.id where e.visit_id=?",Long.class,visit)); }
    @Override public void save(VisitEvidenceFile f) {
        jdbc.update("insert into visit_evidence_file(evidence_id,uploader_user_id,storage_key,content_type,byte_size,sha256,original_sha256,sanitized_original_name,visit_task_id,submitted_at) values (?,?,?,?,?,?,?,?,?,?)",
                f.evidenceId(),f.uploaderUserId(),f.storageKey(),f.contentType(),f.byteSize(),f.sha256(),f.originalSha256(),f.fileName(),f.taskId(),Timestamp.valueOf(f.submittedAt()));
    }
    private static Entry entry(ResultSet row,int n) throws SQLException {
        var at=row.getTimestamp("captured_at");
        var evidence=new VisitEvidence(row.getLong("id"),row.getLong("visit_id"),VisitEvidence.Kind.valueOf(row.getString("kind")),row.getString("reference"),VisitEvidence.VerificationStatus.valueOf(row.getString("verification_status")),at==null ? null : at.toLocalDateTime());
        var file=row.getObject("evidence_id",Long.class)==null ? null : new VisitEvidenceFile(row.getLong("evidence_id"),row.getLong("uploader_user_id"),row.getString("storage_key"),row.getString("content_type"),row.getLong("byte_size"),row.getString("sha256"),row.getString("original_sha256"),row.getString("sanitized_original_name"),row.getObject("visit_task_id",Long.class),row.getTimestamp("submitted_at").toLocalDateTime());
        return new Entry(evidence,file);
    }
}
