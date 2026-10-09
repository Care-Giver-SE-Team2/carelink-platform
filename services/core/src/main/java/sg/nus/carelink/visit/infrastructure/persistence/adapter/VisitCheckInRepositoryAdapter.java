package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.model.CheckInLocation;
import sg.nus.carelink.visit.domain.repository.VisitCheckInRepository;

@Repository
class VisitCheckInRepositoryAdapter implements VisitCheckInRepository {
    private final JdbcTemplate jdbc;
    VisitCheckInRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public java.util.Optional<String> source(Long visitId) {
        return jdbc.query("select location_source from visit_check_in_record where visit_id=?", (rs,n)->rs.getString(1),visitId).stream().findFirst();
    }
    public void save(Long visit, Long actor, UUID key, CheckInLocation loc, LocalDateTime now) {
        jdbc.update("insert into visit_check_in_record(visit_id,actor_user_id,client_request_id,location_source,latitude,longitude,accuracy,location_note,client_captured_at,received_at) values (?,?,?,?,?,?,?,?,?,?)",
                visit, actor, key.toString(), loc.source(), loc.latitude(), loc.longitude(), loc.accuracy(), loc.note(),
                loc.clientCapturedAt() == null ? null : loc.clientCapturedAt().toString(), now);
    }
}
