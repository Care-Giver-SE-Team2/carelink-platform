package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.repository.VisitInstructionRepository;

/** visit.instructions, which the JPA entity leaves unmapped so the roster's saves never touch it. */
@Repository
class VisitInstructionRepositoryAdapter implements VisitInstructionRepository {
    private final JdbcTemplate jdbc;
    VisitInstructionRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<String> find(Long visitId) {
        return jdbc.query("select instructions from visit where id=?", (rs, n) -> rs.getString(1), visitId).stream()
                .filter(text -> text != null && !text.isBlank()).findFirst();
    }
    @Override public void save(Long visitId, String instructions) {
        jdbc.update("update visit set instructions=? where id=?", instructions, visitId);
    }
}
