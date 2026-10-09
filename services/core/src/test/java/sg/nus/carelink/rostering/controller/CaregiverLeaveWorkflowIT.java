package sg.nus.carelink.rostering.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;

class CaregiverLeaveWorkflowIT extends CaregiverHttpITSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry, CaregiverLeaveWorkflowIT.class, null); }

    @Test
    void requestApprovalAndExplicitRerosteringChangeTheTwoCaregiversSchedules() throws Exception {
        try (var a = browser(caregiverName); var b = browser(otherName); var mgr = browser(managerName); var relative = browser(familyName)) {
            var payload = Map.of("type", "SICK", "startDate", day.toString(), "endDate", day.toString(), "reason", "Demo leave");
            long id = body(a.post("/api/caregivers/me/absences", payload), 201).path("id").asLong();
            assertThat(a.read("/api/caregivers/me/absences").toString()).contains("PENDING", "Demo leave");
            assertThat(b.read("/api/caregivers/me/absences").toString()).doesNotContain("Demo leave");
            assertThat(jdbc.queryForObject("select caregiver_id from absence_report where id=?", Long.class, id)).isEqualTo(caregiver);
            assertThat(jdbc.queryForObject("select caregiver_id from visit where id=?", Long.class, visit)).isEqualTo(caregiver);
            assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id=? and event_type='ABSENCE_REQUESTED' and resource_id=?", Long.class, manager, id)).isEqualTo(1L);
            assertThat(a.post("/api/caregivers/me/absences", payload).statusCode()).isEqualTo(409);
            body(mgr.post("/api/absences/" + id + "/approve", Map.of()), 200);
            assertThat(a.read("/api/caregivers/me/absences").toString()).contains("APPROVED");
            assertThat(jdbc.queryForObject("select caregiver_id from visit where id=?", Long.class, visit)).isEqualTo(caregiver);
            body(mgr.post("/api/absences/" + id + "/rerostering-runs", Map.of("objective", "CONTINUITY")), 201);
            long change = jdbc.queryForObject("select id from roster_change where absence_id=? and visit_id=?", Long.class, id, visit);
            body(relative.post("/api/roster-changes/" + change + "/decision", Map.of("choice", "CHANGE_CAREGIVER", "caregiverId", otherCaregiver)), 200);
            assertThat(jdbc.queryForObject("select caregiver_id from visit where id=?", Long.class, visit)).isEqualTo(otherCaregiver);
            String query = "?dateFrom=" + day + "&dateTo=" + day;
            assertThat(a.read("/api/caregivers/me/schedule" + query).path("upcomingVisits")).isEmpty();
            assertThat(b.read("/api/caregivers/me/schedule" + query).path("upcomingVisits").get(0).path("id").asLong()).isEqualTo(visit);
            assertThat(a.get("/api/visits/" + visit + "/work-pack").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void rejectionInvalidDatesRoleAndCsrfFailuresDoNotChangeTheVisit() throws Exception {
        try (var a = browser(caregiverName); var mgr = browser(managerName)) {
            var payload = Map.of("type", "ANNUAL", "startDate", day.toString(), "endDate", day.toString());
            assertThat(a.withoutCsrf("/api/caregivers/me/absences", payload).statusCode()).isEqualTo(403);
            assertThat(mgr.post("/api/caregivers/me/absences", payload).statusCode()).isEqualTo(403);
            assertThat(a.post("/api/caregivers/me/absences", Map.of("startDate", day.toString(), "endDate", day.minusDays(1).toString())).statusCode()).isEqualTo(409);
            long id = body(a.post("/api/caregivers/me/absences", payload), 201).path("id").asLong();
            body(mgr.post("/api/absences/" + id + "/reject", Map.of()), 200);
            assertThat(a.read("/api/caregivers/me/absences").toString()).contains("REJECTED");
            assertThat(jdbc.queryForObject("select caregiver_id from visit where id=?", Long.class, visit)).isEqualTo(caregiver);
            assertThat(a.post("/api/caregivers/me/absences", payload).statusCode()).isEqualTo(201);
            assertThat(a.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
            assertThat(a.get("/api/caregivers/me/absences").statusCode()).isEqualTo(401);
        }
    }
}
