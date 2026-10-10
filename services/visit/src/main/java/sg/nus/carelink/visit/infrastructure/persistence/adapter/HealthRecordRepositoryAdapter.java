package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.model.HealthObservation;
import sg.nus.carelink.visit.domain.model.HealthRecord;
import sg.nus.carelink.visit.domain.repository.HealthRecordRepository;

@Repository
class HealthRecordRepositoryAdapter implements HealthRecordRepository {
    private final JdbcTemplate jdbc;
    HealthRecordRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public HealthRecord save(HealthRecord record, Long actorUserId) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into visit_health_record(visit_id,recorded_by_user_id,health_flag,health_note,recorded_at)
                    values (?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, record.visitId());
            statement.setLong(2, actorUserId);
            statement.setString(3, record.healthFlag().name());
            statement.setString(4, record.healthNote());
            statement.setTimestamp(5, Timestamp.valueOf(record.recordedAt()));
            return statement;
        }, key);
        long id = java.util.Objects.requireNonNull(key.getKey()).longValue();
        for (var reading : record.readings()) {
            jdbc.update("""
                    insert into vital_sign(visit_id,metric,value,unit,out_of_range,recorded_at,health_record_id)
                    values (?,?,?,?,false,?,?)
                    """, record.visitId(), reading.metric(), reading.value(), reading.unit(),
                    Timestamp.valueOf(record.recordedAt()), id);
        }
        return new HealthRecord(id, record.visitId(), record.healthFlag(), record.healthNote(), record.recordedAt(), record.readings());
    }

    @Override
    public Optional<HealthRecord> find(Long visitId, Long recordId) {
        return jdbc.query("select * from visit_health_record where visit_id=? and id=?", this::record, visitId, recordId)
                .stream().findFirst().map(this::withReadings);
    }

    @Override
    public List<HealthRecord> history(Long visitId, int offset, int limit) {
        return jdbc.query("""
                select * from visit_health_record where visit_id=? order by recorded_at desc,id desc limit ? offset ?
                """, this::record, visitId, limit, offset).stream().map(this::withReadings).toList();
    }

    @Override
    public long count(Long visitId) {
        return java.util.Objects.requireNonNull(jdbc.queryForObject("select count(*) from visit_health_record where visit_id=?", Long.class, visitId));
    }

    private HealthRecord record(ResultSet row, int index) throws SQLException {
        return new HealthRecord(row.getLong("id"), row.getLong("visit_id"),
                HealthObservation.Flag.valueOf(row.getString("health_flag")), row.getString("health_note"),
                row.getTimestamp("recorded_at").toLocalDateTime(), List.of());
    }

    private HealthRecord withReadings(HealthRecord record) {
        var readings = jdbc.query("""
                select metric,value,unit from vital_sign where visit_id=? and health_record_id=?
                order by case metric when 'systolic' then 1 when 'diastolic' then 2 when 'pulse' then 3 else 4 end,id
                """, (row, index) -> new HealthRecord.Reading(row.getString("metric"), row.getBigDecimal("value"), row.getString("unit")),
                record.visitId(), record.id());
        return new HealthRecord(record.id(), record.visitId(), record.healthFlag(), record.healthNote(), record.recordedAt(), readings);
    }
}
