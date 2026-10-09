package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;

class CaregiverSpotCheckWorkflowIT extends CaregiverHttpITSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry, CaregiverSpotCheckWorkflowIT.class, null); }

    @Test
    void approvedAndConcludedCheckNotifiesItsCaregiverWhoCanRespondAndUpdate() throws Exception {
        try (var a = browser(caregiverName); var b = browser(otherName); var mgr = browser(managerName); var relative = browser(familyName)) {
            long id = body(mgr.post("/api/spot-checks", Map.of("visitId", visit, "purpose", "Demo quality check")), 201).path("id").asLong();
            assertThat(a.read("/api/spot-checks")).isEmpty();
            assertThat(a.post("/api/spot-checks/" + id + "/response", Map.of("response", "Too soon")).statusCode()).isEqualTo(409);
            body(relative.post("/api/spot-checks/" + id + "/decision", Map.of("approve", true)), 200);
            body(mgr.post("/api/spot-checks/" + id + "/conclusion", Map.of("result", "NEEDS_IMPROVEMENT", "notes", "Explain the routine")), 200);
            assertThat(a.read("/api/spot-checks").toString()).contains("Demo quality check", "NEEDS_IMPROVEMENT");
            assertThat(b.read("/api/spot-checks")).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id=? and event_type='SPOT_CHECK_CONCLUDED' and resource_id=?", Long.class, caregiverUser, id)).isEqualTo(1L);
            assertThat(b.post("/api/spot-checks/" + id + "/response", Map.of("response", "Not mine")).statusCode()).isEqualTo(409);
            assertThat(a.withoutCsrf("/api/spot-checks/" + id + "/response", Map.of("response", "No token")).statusCode()).isEqualTo(403);
            assertThat(a.post("/api/spot-checks/" + id + "/response", Map.of("response", "   ")).statusCode()).isEqualTo(400);
            body(a.post("/api/spot-checks/" + id + "/response", Map.of("response", "I will explain each step.")), 200);
            body(a.post("/api/spot-checks/" + id + "/response", Map.of("response", "Updated response")), 200);
            assertThat(mgr.read("/api/spot-checks").toString()).contains("Updated response");
            assertThat(jdbc.queryForObject("select result from spot_check where id=?", String.class, id)).isEqualTo("NEEDS_IMPROVEMENT");
            assertThat(jdbc.queryForObject("select caregiver_id from visit where id=?", Long.class, visit)).isEqualTo(caregiver);
            assertThat(relative.read("/api/spot-checks").toString()).contains("COMPLETED");
            assertThat(a.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
            assertThat(a.get("/api/spot-checks").statusCode()).isEqualTo(401);
        }
    }
}
