package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;

class CaregiverHealthMigrationIT extends CaregiverHttpITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, CaregiverHealthMigrationIT.class, null);
    }
    @Autowired DataSource dataSource;
    @Test void upgradesV19WithoutChangingExistingFactsOrInventingNormalHealth() throws Exception {
        String url;
        try (var connection = dataSource.getConnection()) {
            String schema = connection.getCatalog() + "_upgrade";
            connection.createStatement().execute("create database " + schema);
            url = connection.getMetaData().getURL().replace("/" + connection.getCatalog(), "/" + schema);
        }
        var source = new DriverManagerDataSource(url, "test", "test");
        // SharedMySql uses Testcontainers' standard test/test account.
        var migration = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("19").load();
        migration.migrate();
        var old = new JdbcTemplate(source);
        old.update("insert into elder(id,full_name) values (90001,'Synthetic migration elder')");
        old.update("insert into visit(id,elder_id,scheduled_start,status) values (90001,90001,'2026-10-09 09:00:00','SCHEDULED')");
        old.update("insert into vital_sign(visit_id,metric,value,unit,out_of_range,recorded_at) values (90001,'pulse',73,'bpm',true,'2026-10-09 09:10:00')");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        assertThat(old.queryForObject("select count(*) from vital_sign where visit_id=90001", Long.class)).isEqualTo(1);
        assertThat(old.queryForObject("select health_record_id from vital_sign where visit_id=90001", Long.class)).isNull();
        assertThat(old.queryForObject("select out_of_range from vital_sign where visit_id=90001", Boolean.class)).isTrue();
        assertThat(old.queryForObject("select health_flag from visit where id=90001", String.class)).isNull();
        assertThat(old.queryForObject("select recorded_at from vital_sign where visit_id=90001", String.class)).startsWith("2026-10-09 09:10:00");
        assertThatThrownBy(() -> old.update("update visit set health_flag='UNKNOWN' where id=90001"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("chk_visit_health_flag");
        assertThat(Flyway.configure().dataSource(source).locations("classpath:db/migration").load().validateWithResult().validationSuccessful).isTrue();
    }
}
