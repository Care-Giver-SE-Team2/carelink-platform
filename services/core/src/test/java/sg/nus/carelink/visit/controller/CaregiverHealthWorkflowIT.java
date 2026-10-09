package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import sg.nus.carelink.testsupport.CaregiverHealthITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;

class CaregiverHealthWorkflowIT extends CaregiverHealthITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, CaregiverHealthWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
    }
    @MockitoSpyBean CaregiverCommandStore receipts;

    @Test void realMeasurementFeedsFiledReportsAndKeepsHealthSummaryAndHistory() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName); var mgr = browser(managerName); var familyBrowser = browser(familyName)) {
            checkIn(a, id);
            var input = health(1);
            var saved = body(a.post(path(id), input), 201);
            assertThat(saved.path("visitVersion").asInt()).isEqualTo(2);
            assertThat(saved.path("record").path("readings").size()).isEqualTo(4);
            long recordId = saved.path("record").path("id").asLong();
            assertThat(count("vital_sign", id)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select health_flag from visit where id=?", String.class, id)).isEqualTo("ATTENTION");
            assertThat(jdbc.queryForObject("select status from visit where id=?", String.class, id)).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("select checked_out_at from visit where id=?", String.class, id)).isNull();
            assertThat(jdbc.queryForObject("select count(*) from vital_sign s join visit_health_record h on h.id=s.health_record_id where s.visit_id=? and s.recorded_at=h.recorded_at", Long.class, id)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select min(out_of_range) from vital_sign where visit_id=?", Boolean.class, id)).isFalse();
            assertThat(a.read("/api/visits/" + id + "/work-pack").path("healthObservation").path("healthFlag").asString()).isEqualTo("ATTENTION");
            input.put("temperature", new BigDecimal("36.70"));
            var replay = body(a.post(path(id), input), 200);
            assertThat(replay.path("record").path("id").asLong()).isEqualTo(recordId);
            assertThat(replay.path("record").path("recordedAt").asString()).isEqualTo(saved.path("record").path("recordedAt").asString());
            input.put("pulse", 74);
            assertThat(a.post(path(id), input).statusCode()).isEqualTo(409);
            var again = health(2); again.put("healthFlag", "MEDICAL_REVIEW");
            body(a.post(path(id), again), 201);
            assertThat(count("vital_sign", id)).isEqualTo(8);
            var history = a.read(path(id) + "?size=1");
            assertThat(history.path("total").asLong()).isEqualTo(2);
            assertThat(history.path("items").get(0).path("healthFlag").asString()).isEqualTo("MEDICAL_REVIEW");
            assertThat(a.read(path(id) + "?size=1&page=1").path("items").get(0).path("id").asLong()).isEqualTo(recordId);
            assertThat(a.read(path(id)).toString()).doesNotContain("recordedByUserId", "payloadHash");
            assertThat(familyBrowser.read("/api/visits/" + id).toString()).doesNotContain("healthNote", "Synthetic caregiver observation");
            assertThat(familyBrowser.read("/api/visits/" + id + "/timeline").size()).isEqualTo(2);
            var today = LocalDate.now(ZoneId.of("Asia/Singapore"));
            var reports = body(mgr.post("/api/reports/generate", Map.of("elderId", elder, "periodStart", today.toString(), "periodEnd", today.toString())), 202);
            for (var report : reports) {
                assertThat(mgr.read("/api/reports/" + report.path("id").asLong()).toString())
                        .contains("123", "81", "73", "36.7").doesNotContain("Synthetic caregiver observation");
            }
            long familyReport = 0;
            for (var report : reports) { if ("FAMILY".equals(report.path("audience").asString())) familyReport = report.path("id").asLong(); }
            assertThat(familyReport).isPositive();
            assertThat(familyBrowser.read("/api/reports/" + familyReport).toString()).contains("123", "36.7").doesNotContain("healthNote", "Synthetic caregiver observation");
            assertThat(jdbc.queryForObject("select count(*) from report_basis where elder_id=? and facts like '%123%'", Long.class, elder)).isEqualTo(1);
        }
    }

    @Test void validatesBloodPressureConcernsMissingMeasurementsAndPagination() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName)) {
            checkIn(a, id);
            for (String field : List.of("systolic", "healthFlag", "healthNote")) {
                var input = health(1); input.remove(field);
                assertThat(a.post(path(id), input).statusCode()).isEqualTo(400);
            }
            for (Object value : List.of(-1, 0, 73.5, 1000000, "not-a-number")) {
                var input = health(1); input.put("pulse", value);
                assertThat(a.post(path(id), input).statusCode()).isEqualTo(400);
            }
            var invalid = health(1); invalid.put("temperature", 36.777);
            assertThat(a.post(path(id), invalid).statusCode()).isEqualTo(400);
            invalid = health(1); invalid.put("healthFlag", "DIAGNOSED");
            assertThat(a.post(path(id), invalid).statusCode()).isEqualTo(400);
            invalid = health(1); invalid.put("healthNote", "a".repeat(1001));
            assertThat(a.post(path(id), invalid).statusCode()).isEqualTo(400);
            var partial = health(1); partial.remove("systolic"); partial.remove("diastolic"); partial.remove("temperature"); partial.put("healthFlag", "NO_CONCERN"); partial.remove("healthNote");
            body(a.post(path(id), partial), 201); assertThat(count("vital_sign", id)).isEqualTo(1);
            var unmeasured = Map.<String, Object>of("expectedVersion", 2, "clientRequestId", UUID.randomUUID().toString(), "healthFlag", "ATTENTION", "healthNote", "No device reading obtained");
            body(a.post(path(id), unmeasured), 201); assertThat(count("vital_sign", id)).isEqualTo(1);
            for (String query : List.of("?size=0", "?size=51", "?page=-1", "?page=100001")) assertThat(a.get(path(id) + query).statusCode()).isEqualTo(400);
        }
    }

    @Test void checksRolesCsrfAssignmentStateAndCrossCommandKeys() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName); var b = browser(otherName); var familyBrowser = browser(familyName)) {
            assertThat(a.post(path(id), health(0)).statusCode()).isEqualTo(409);
            checkIn(a, id);
            assertThat(a.withoutCsrf(path(id), health(1)).statusCode()).isEqualTo(403);
            assertThat(b.post(path(id), health(1)).statusCode()).isEqualTo(403);
            assertThat(b.get(path(id)).statusCode()).isEqualTo(403);
            assertThat(familyBrowser.post(path(id), health(1)).statusCode()).isEqualTo(403);
            assertThat(familyBrowser.get(path(id)).statusCode()).isEqualTo(403);
            var input = health(1);
            input.put("clientRequestId", jdbc.queryForObject("select client_request_id from caregiver_command_receipt where visit_id=? and action_code='CHECK_IN'", String.class, id));
            assertThat(a.post(path(id), input).statusCode()).isEqualTo(409);
            var taskId = a.read("/api/visits/" + id + "/work-pack").path("tasks").get(0).path("id").asLong();
            body(a.post("/api/visits/" + id + "/tasks/" + taskId + "/complete", Map.of("expectedVersion", 1, "clientRequestId", UUID.randomUUID().toString(), "status", "DONE")), 200);
            assertThat(a.post(path(id), health(1)).statusCode()).isEqualTo(409);
            var good = health(2); body(a.post(path(id), good), 201);
            body(a.post("/api/incidents", Map.of("visitId", id, "expectedVersion", 3, "clientRequestId", UUID.randomUUID().toString(), "category", "SERVICE", "severity", "LOW", "description", "Synthetic exception")), 201);
            assertThat(a.post(path(id), health(4)).statusCode()).isEqualTo(409);
            assertThat(body(a.post(path(id), good), 200).path("replayed").asBoolean()).isTrue();
            assertThat(count("vital_sign", id)).isEqualTo(4);
            assertThat(a.read("/api/visits/" + id + "/work-pack").path("healthObservation").path("healthFlag").asString()).isEqualTo("ATTENTION");
        }
    }

    @Test void receiptStorageFailureRollsBackEveryHealthFactAndParentVersion() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName)) {
            checkIn(a, id);
            doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic receipt failure")).when(receipts).save(any());
            assertThat(a.post(path(id), health(1)).statusCode()).isEqualTo(503);
        }
        assertThat(count("visit_health_record", id)).isZero();
        assertThat(count("vital_sign", id)).isZero();
        assertThat(jdbc.queryForObject("select health_flag from visit where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select version from visit where id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from caregiver_command_receipt where visit_id=? and action_code='HEALTH_RECORD'", Long.class, id)).isZero();
    }

    @Test void thirdReadingFailureRollsBackTheFirstTwoReadingsAndBatch() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName)) {
            checkIn(a, id);
            jdbc.execute("""
                    create trigger reject_health_pulse before insert on vital_sign for each row
                    begin if NEW.metric='pulse' then signal SQLSTATE '45000' set MESSAGE_TEXT='synthetic pulse fault'; end if; end
                    """);
            try { assertThat(a.post(path(id), health(1)).statusCode()).isEqualTo(503); }
            finally { jdbc.execute("drop trigger reject_health_pulse"); }
            assertThat(count("vital_sign", id)).isZero();
            assertThat(count("visit_health_record", id)).isZero();
            assertThat(jdbc.queryForObject("select version from visit where id=?", Integer.class, id)).isEqualTo(1);
        }
    }

    @Test void allTerminalStatesRejectNewMeasurementsAndOldVisitJsonDoesNotExposeHealth() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName)) {
            checkIn(a, id);
            var original = health(1);
            body(a.post(path(id), original), 201);
            for (String state : List.of("COMPLETED", "VERIFIED", "AUTO_CLOSED", "CANCELLED", "EXCEPTION")) {
                // Negative fixture only: no completed visit is used as a positive workflow.
                jdbc.update("update visit set status=? where id=?", state, id);
                assertThat(a.post(path(id), health(2)).statusCode()).isEqualTo(409);
                if (state.equals("CANCELLED")) {
                    assertThat(a.post(path(id), original).statusCode()).isEqualTo(409);
                    assertThat(a.get(path(id)).statusCode()).isEqualTo(409);
                }
            }
            assertThat(count("vital_sign", id)).isEqualTo(4);
        }
    }
}
