package sg.nus.carelink.testsupport;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Positive fixtures are produced the way a published plan produces them, and through caregiver check-in. */
public abstract class CaregiverHealthITSupport extends CaregiverHttpITSupport {
    protected long plannedVisit() {
        var start = LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusMinutes(5).withSecond(0).withNano(0);
        return publishPlan(start, 60, "NONE", "Care routine").visits().getFirst();
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
