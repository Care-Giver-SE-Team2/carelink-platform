package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.repository.VisitCheckInRepository;

@Repository
class VisitCheckInRepositoryAdapter implements VisitCheckInRepository {
    private final JdbcTemplate jdbc;
    VisitCheckInRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void save(Long visit, Long actor, UUID key, LocalDateTime now) {
        jdbc.update("insert into visit_check_in_record(visit_id,actor_user_id,client_request_id,received_at) values (?,?,?,?)",
                visit, actor, key.toString(), now);
    }
}
