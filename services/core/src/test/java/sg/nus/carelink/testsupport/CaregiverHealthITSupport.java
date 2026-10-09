package sg.nus.carelink.testsupport;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Positive fixtures are produced through manager plan publication and caregiver check-in. */
public abstract class CaregiverHealthITSupport extends CaregiverHttpITSupport {
    protected long plannedVisit() throws Exception {
        var start = LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusMinutes(5).withSecond(0).withNano(0);
        try (var mgr = browser(managerName)) {
            body(mgr.put("/api/elders/" + elder + "/primary-caregiver", Map.of("caregiverId", caregiver)), 200);
            long plan = body(mgr.post("/api/care-plans", Map.of("elderId", elder)), 201).path("id").asLong();
            body(mgr.post("/api/care-plans/" + plan + "/publish", Map.of("startDate", start.toLocalDate().toString(), "nodes", List.of(Map.of(
                    "groupName", "Personal care", "name", "Care routine", "evidenceType", "NONE", "visits", List.of(Map.of(
                    "day", start.getDayOfWeek().name(), "startTime", start.toLocalTime().toString(), "minutes", 60)))))), 200);
            return jdbc.queryForObject("select id from visit where care_plan_id=? and scheduled_start=?", Long.class, plan, Timestamp.valueOf(start));
        }
    }
    protected void checkIn(Browser browser, long id) throws Exception {
        body(browser.post("/api/visits/" + id + "/check-in", Map.of("expectedVersion", 0, "clientRequestId", UUID.randomUUID().toString(),
                "locationSource", "MANUAL_LOCATION_NOTE", "locationNote", "At service doorway")), 200);
    }
    protected Map<String, Object> health(int version) {
        return new HashMap<>(Map.of("expectedVersion", version, "clientRequestId", UUID.randomUUID().toString(),
                "systolic", 123, "diastolic", 81, "pulse", 73, "temperature", 36.7, "healthFlag", "ATTENTION", "healthNote", "Synthetic caregiver observation"));
    }
    protected String path(long id) { return "/api/visits/" + id + "/health-records"; }
    protected long count(String table, long id) {
        // Fixture-only table names, never input from HTTP requests.
        return jdbc.queryForObject("select count(*) from " + table + " where visit_id=?", Long.class, id);
    }
}
