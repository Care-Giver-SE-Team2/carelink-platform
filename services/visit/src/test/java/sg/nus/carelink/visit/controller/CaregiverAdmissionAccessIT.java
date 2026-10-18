package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;

/** Visit's real HTTP/MySQL with the core API stand-in; real multi-service acceptance is separate. */
class CaregiverAdmissionAccessIT extends CaregiverHttpITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, CaregiverAdmissionAccessIT.class, "+08:00");
    }
    @Test void pendingApplicantCannotUseEvenAnAssignedVisitAndApprovalUnlocksSameSession() throws Exception {
        jdbc.update("update caregiver set status='ONBOARDING' where id=?", caregiver);
        try (var applicant = browser(caregiverName)) {
            assertThat(applicant.read("/api/caregivers/me").path("status").asString()).isEqualTo("ONBOARDING");
            for (String path : new String[]{"/api/caregivers/me/schedule", "/api/visits/"+visit+"/work-pack",
                    "/api/visits/"+visit+"/tasks", "/api/visits/"+visit+"/health-records", "/api/caregivers/me/incidents"}) {
                assertThat(applicant.get(path).statusCode()).as(path).isEqualTo(403);
            }
            var version = Map.of("expectedVersion", 0, "clientRequestId", UUID.randomUUID().toString());
            assertThat(applicant.post("/api/visits/"+visit+"/check-in", Map.of("expectedVersion",0,
                    "clientRequestId",UUID.randomUUID().toString(),"locationSource","MANUAL_LOCATION_NOTE",
                    "locationNote","At the service doorway")).statusCode()).isEqualTo(403);
            assertThat(applicant.post("/api/visits/"+visit+"/check-out", version).statusCode()).isEqualTo(403);
            assertThat(applicant.post("/api/visits/"+visit+"/tasks/1/complete", Map.of("expectedVersion",0,
                    "clientRequestId",UUID.randomUUID().toString(),"status","DONE","outcome","Completed",
                    "caregiverNote","Test note")).statusCode()).isEqualTo(403);
            assertThat(applicant.post("/api/caregivers/me/incidents", Map.of("visitId",visit,"expectedVersion",0,
                    "clientRequestId",UUID.randomUUID().toString(),"category","SERVICE","severity","LOW",
                    "description","Test incident")).statusCode()).isEqualTo(403);
            assertThat(jdbc.queryForObject("select count(*) from caregiver_command_receipt",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("SCHEDULED");
            // Represent the changed response from core; this does not test core's approval command.
            jdbc.update("update caregiver set status='AVAILABLE' where id=?", caregiver);
            assertThat(applicant.get("/api/caregivers/me/schedule").statusCode()).isEqualTo(200);
            assertThat(applicant.get("/api/visits/"+visit+"/work-pack").statusCode()).isEqualTo(200);
        }
    }
}
