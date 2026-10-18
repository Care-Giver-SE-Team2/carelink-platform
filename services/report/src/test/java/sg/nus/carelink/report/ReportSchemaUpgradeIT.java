package sg.nus.carelink.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import sg.nus.carelink.testsupport.SharedMySql;

/** Real MySQL upgrades preserve main's applied V4 and legacy evidence counters. */
class ReportSchemaUpgradeIT {
    @Test
    void mainV4UpgradesWithoutChangingDispatchClaimsOrAppliedChecksums() {
        Database db = database();
        migration(db, "4").migrate();
        db.jdbc().update("insert into report_task_dispatch(elder_id,period_start,period_end) "
                + "values (42,'2026-09-14','2026-09-20')");
        Map<String, Object> before = db.jdbc().queryForMap(
                "select script,checksum,installed_rank from flyway_schema_history where version='4'");

        migration(db, null).migrate();
        migration(db, null).validate();

        assertThat(db.jdbc().queryForMap(
                "select script,checksum,installed_rank from flyway_schema_history where version='4'"))
                .isEqualTo(before);
        assertThat(before.get("script")).isEqualTo("V4__report_task_dispatch.sql");
        assertThat(db.jdbc().queryForObject("select count(*) from report_task_dispatch where elder_id=42",
                Integer.class)).isEqualTo(1);
        assertThat(db.jdbc().queryForObject("select script from flyway_schema_history where version='5'",
                String.class)).isEqualTo("V5__visit_evidence_projection.sql");
        assertThat(db.jdbc().queryForObject("select count(*) from report_visit_evidence", Integer.class)).isZero();
        assertThat(migration(db, null).migrate().migrationsExecuted).isZero();
    }

    @Test
    void legacyV3UpgradesThroughBothFeaturesWithoutInventingEvidenceSnapshots() {
        Database db = database();
        migration(db, "3").migrate();
        db.jdbc().update("insert into report_visit(visit_id,elder_id,scheduled_start,status,version,"
                + "evidence_count,verified_evidence_count) values (7,42,'2026-09-15 09:00:00','COMPLETED',9,7,3)");
        Map<String, Object> before = db.jdbc().queryForMap("select * from report_visit where visit_id=7");

        migration(db, null).migrate();
        migration(db, null).validate();

        assertThat(db.jdbc().queryForMap("select * from report_visit where visit_id=7")).isEqualTo(before);
        assertThat(db.jdbc().queryForObject("select count(*) from report_task_dispatch", Integer.class)).isZero();
        assertThat(db.jdbc().queryForObject("select count(*) from report_visit_evidence", Integer.class)).isZero();
    }

    private static Database database() {
        Map<String, Supplier<Object>> properties = new HashMap<>();
        SharedMySql.register(properties::put, ReportSchemaUpgradeIT.class, null,
                "connectionTimeZone=Asia/Singapore");
        var dataSource = new DriverManagerDataSource(
                (String) properties.get("spring.datasource.url").get(),
                (String) properties.get("spring.datasource.username").get(),
                (String) properties.get("spring.datasource.password").get());
        return new Database(dataSource, new JdbcTemplate(dataSource));
    }

    private static Flyway migration(Database db, String target) {
        var configuration = Flyway.configure().dataSource(db.dataSource())
                .locations("classpath:db/migration").cleanDisabled(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private record Database(DriverManagerDataSource dataSource, JdbcTemplate jdbc) { }
}
