package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.model.CaregiverCommandReceipt;
import sg.nus.carelink.visit.domain.repository.CaregiverHistoryStore;

@Repository
class JdbcCaregiverHistoryStore implements CaregiverHistoryStore {
    private final JdbcTemplate jdbc;
    JdbcCaregiverHistoryStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String FILTER=" from caregiver_command_receipt r join visit v on v.id=r.visit_id where r.actor_user_id=? and (? is null or v.status=?) and r.visit_id in (select m.visit_id from caregiver_command_receipt m where m.actor_user_id=? and m.occurred_at>=? and m.occurred_at<?)";
    @Override public List<VisitSummary> visits(Long actor,LocalDateTime from,LocalDateTime until,String status,int offset,int size) {
        return jdbc.query("select r.visit_id,v.status,min(r.occurred_at) as first_at,max(r.occurred_at) as last_at,max(case when r.action_code='CHECK_IN' then r.occurred_at end) as checked_in,max(case when r.action_code='CHECK_OUT' then r.occurred_at end) as checked_out,count(*) as commands"+FILTER+" group by r.visit_id,v.status order by last_at desc,r.visit_id desc limit ? offset ?",
                (row,n)->new VisitSummary(row.getLong("visit_id"),row.getString("status"),time(row,"first_at"),time(row,"last_at"),time(row,"checked_in"),time(row,"checked_out"),row.getLong("commands")),actor,status,status,actor,Timestamp.valueOf(from),Timestamp.valueOf(until),size,offset);
    }
    @Override public long countVisits(Long actor,LocalDateTime from,LocalDateTime until,String status) {
        return Objects.requireNonNull(jdbc.queryForObject("select count(distinct r.visit_id)"+FILTER,Long.class,actor,status,status,actor,Timestamp.valueOf(from),Timestamp.valueOf(until)));
    }
    @Override public List<CaregiverCommandReceipt> commands(Long actor,Long visit,int offset,int size) {
        // The parent version is causal order. Older arrival/departure timestamps have
        // second precision whereas evidence has microseconds; sorting by time alone
        // can incorrectly place check-out before an earlier upload in the same second.
        return jdbc.query("select * from caregiver_command_receipt where actor_user_id=? and visit_id=? order by visit_version desc,occurred_at desc,client_request_id limit ? offset ?",
                (row,n)->new CaregiverCommandReceipt(row.getLong("actor_user_id"),UUID.fromString(row.getString("client_request_id")),row.getString("action_code"),row.getLong("visit_id"),row.getString("payload_hash"),row.getLong("result_id"),row.getInt("visit_version"),time(row,"occurred_at")),actor,visit,size,offset);
    }
    @Override public long countCommands(Long actor,Long visit) { return Objects.requireNonNull(jdbc.queryForObject("select count(*) from caregiver_command_receipt where actor_user_id=? and visit_id=?",Long.class,actor,visit)); }
    private static LocalDateTime time(ResultSet row,String column) throws SQLException { var value=row.getTimestamp(column); return value==null ? null : value.toLocalDateTime(); }
}
