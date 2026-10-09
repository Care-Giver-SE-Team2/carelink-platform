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

/** Positive Visit setup uses real manager commands, not Visit/Incident/notification SQL. */
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
    protected long plannedVisit() throws Exception { return plannedVisits(1).getFirst(); }
    protected List<Long> plannedVisits(int count) throws Exception {
        try (var mgr = browser(managerName)) {
            body(mgr.put("/api/elders/" + elder + "/primary-caregiver", Map.of("caregiverId", caregiver)), 200);
            long plan = body(mgr.post("/api/care-plans", Map.of("elderId", elder)), 201).path("id").asLong();
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (int i=0; i<count; i++) nodes.add(Map.of("groupName", "Personal care", "name", "SYS03 routine " + i,
                    "evidenceType", "CHECKLIST", "visits", List.of(Map.of("day", "THURSDAY", "startTime", "10:05", "minutes", 60))));
            body(mgr.post("/api/care-plans/" + plan + "/publish", Map.of("startDate", "2026-10-08", "nodes", nodes)), 200);
            List<Long> ids = new ArrayList<>();
            for (var row : mgr.read("/api/visits/roster?date=2026-10-08")) {
                if (row.path("carePlanId").asLong()==plan) ids.add(row.path("id").asLong());
            }
            org.assertj.core.api.Assertions.assertThat(ids).hasSize(count);
            return ids;
        }
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
    protected long count(String table, long id) {
        if (!Set.of("incident", "visit_missed_check_in_trigger", "visit_state_transition", "visit_check_in_record").contains(table)) throw new IllegalArgumentException("Unknown fixture table");
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE visit_id=?", Long.class, id);
    }
}
