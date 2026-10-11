package sg.nus.carelink.testsupport;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import sg.nus.carelink.visit.application.MissedCheckInScanService;

/**
 * Positive Visit setup goes the way a manager's published plan reaches visit, not through Visit SQL.
 * The incidents a missed check-in raises are core's, so the tests read them from the core stand-in.
 */
@Import(MissedCheckInITSupport.TimeConfiguration.class)
public abstract class MissedCheckInITSupport extends CaregiverHttpITSupport {
    protected static final Instant START = Instant.parse("2026-10-08T02:00:00Z");
    protected static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    @Autowired protected ScenarioClock clock;
    @Autowired protected MissedCheckInScanService scan;
    protected String secondFamilyName;
    protected long secondFamily, secondFamilyUser;
    @TestConfiguration(proxyBeanMethods=false) public static class TimeConfiguration {
        @Bean @Primary ScenarioClock sys03Clock() { return new ScenarioClock(); }
    }
    public static class ScenarioClock extends Clock {
        private volatile Instant current = START;
        public void at(Instant value) { current = value; }
        @Override public ZoneId getZone() { return SGT; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
        @Override public Instant instant() { return current; }
    }
    @BeforeEach void isolateRoutingAndTime() {
        clock.at(START);
        jdbc.update("DELETE FROM user_role WHERE role='MANAGER' AND user_id<>?", manager);
        secondFamilyName = "sys03-family-b-" + UUID.randomUUID().toString().substring(0, 8);
        secondFamilyUser = insert("INSERT INTO app_user(username,password_hash,display_name) VALUES (?,'{noop}test-password','Fictional read-only family')", secondFamilyName);
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'FAMILY')", secondFamilyUser);
        secondFamily = insert("INSERT INTO family_member(user_id,full_name) VALUES (?,'Fictional read-only family')", secondFamilyUser);
        jdbc.update("INSERT INTO elder_family_binding(elder_id,family_member_id,relationship,access_scope,status) VALUES (?,?,'SON','READ_ONLY','ACTIVE')", elder, secondFamily);
    }
    protected long plannedVisit() { return plannedVisits(1).getFirst(); }
    protected List<Long> plannedVisits(int count) {
        String[] tasks = new String[count];
        for (int i=0; i<count; i++) tasks[i] = "SYS03 routine " + i;
        List<Long> ids = publishPlan(LocalDateTime.of(2026, 10, 8, 10, 5), 60, "CHECKLIST", tasks).visits();
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(count);
        return ids;
    }
    protected void overdue() { clock.at(START.plusSeconds(901)); }
    protected Map<String,Object> check(int version) {
        return Map.of("expectedVersion",version,"clientRequestId",UUID.randomUUID().toString(),
                "locationSource","MANUAL_LOCATION_NOTE","locationNote","At the fictional doorway");
    }
    protected long incident(long id) {
        return jdbc.queryForObject("SELECT incident_id FROM visit_missed_check_in_trigger WHERE visit_id=?", Long.class, id);
    }
    protected int version(long id) { return jdbc.queryForObject("SELECT version FROM visit WHERE id=?", Integer.class, id); }
    /** The visit's rows in one of visit's tables, or for "incident", the incidents visit asked core to raise about it. */
    protected long count(String table, long id) {
        if (table.equals("incident")) return core.incidentsOf(id).size();
        if (!Set.of("visit_missed_check_in_trigger", "visit_state_transition", "visit_check_in_record").contains(table)) throw new IllegalArgumentException("Unknown fixture table");
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE visit_id=?", Long.class, id);
    }
}
