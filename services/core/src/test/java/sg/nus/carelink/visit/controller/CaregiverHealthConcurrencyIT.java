package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import sg.nus.carelink.testsupport.CaregiverHealthITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;

/** Hold real Visit locks to exercise both commit orders rather than relying on sleeps. */
class CaregiverHealthConcurrencyIT extends CaregiverHealthITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, CaregiverHealthConcurrencyIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
    }
    @MockitoSpyBean VisitCommandRepository commands;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    @AfterEach void stop() { workers.shutdownNow(); reset(commands); }
    private record Gate(CountDownLatch held, CountDownLatch entered, CountDownLatch release) {
        Gate() { this(new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1)); }
    }
    private Gate hold(long id) {
        var gate = new Gate(); var first = new AtomicBoolean(true);
        doAnswer(call -> {
            if (first.compareAndSet(true, false)) {
                Object value = call.callRealMethod(); gate.held().countDown(); await(gate.release()); return value;
            }
            gate.entered().countDown(); return call.callRealMethod();
        }).when(commands).lock(id);
        return gate;
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(15, TimeUnit.SECONDS)).as("controlled Visit lock boundary").isTrue();
    }
    @Test void identicalConcurrentRequestsReturnOneMeasurementAndOneReplay() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName); var b = browser(caregiverName)) {
            checkIn(a, id); var input = health(1); var gate = hold(id);
            var first = workers.submit(() -> a.post(path(id), input)); await(gate.held());
            var second = workers.submit(() -> b.post(path(id), input)); await(gate.entered());
            gate.release().countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS).statusCode(), second.get(20, TimeUnit.SECONDS).statusCode())).containsExactly(201, 200);
            assertThat(count("visit_health_record", id)).isEqualTo(1); assertThat(count("vital_sign", id)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select version from visit where id=?", Integer.class, id)).isEqualTo(2);
        }
    }
    @Test void differentConcurrentMeasurementsConflictUntilExplicitRefresh() throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName); var b = browser(caregiverName)) {
            checkIn(a, id); var gate = hold(id);
            var first = workers.submit(() -> a.post(path(id), health(1))); await(gate.held());
            var second = workers.submit(() -> b.post(path(id), health(1))); await(gate.entered());
            gate.release().countDown(); body(first.get(20, TimeUnit.SECONDS), 201); body(second.get(20, TimeUnit.SECONDS), 409);
            body(b.post(path(id), health(2)), 201);
            assertThat(count("visit_health_record", id)).isEqualTo(2); assertThat(count("vital_sign", id)).isEqualTo(8);
        }
    }
    @ParameterizedTest
    @CsvSource({"true,INCIDENT", "false,INCIDENT", "true,TASK", "false,TASK"})
    void measurementAndOtherCommandRespectBothCommitOrders(boolean healthFirst, String other) throws Exception {
        long id = plannedVisit();
        try (var a = browser(caregiverName); var b = browser(caregiverName)) {
            checkIn(a, id); long taskId = a.read("/api/visits/" + id + "/work-pack").path("tasks").get(0).path("id").asLong();
            String otherPath = "INCIDENT".equals(other) ? "/api/incidents" : "/api/visits/" + id + "/tasks/" + taskId + "/complete";
            Map<String, Object> otherInput = "INCIDENT".equals(other)
                    ? Map.of("visitId", id, "expectedVersion", 1, "clientRequestId", UUID.randomUUID().toString(), "category", "SERVICE", "severity", "LOW", "description", "Synthetic paused visit")
                    : Map.of("expectedVersion", 1, "clientRequestId", UUID.randomUUID().toString(), "status", "DONE");
            var gate = hold(id);
            var first = workers.submit(() -> a.post(healthFirst ? path(id) : otherPath, healthFirst ? health(1) : otherInput)); await(gate.held());
            var second = workers.submit(() -> b.post(healthFirst ? otherPath : path(id), healthFirst ? otherInput : health(1))); await(gate.entered());
            gate.release().countDown();
            body(first.get(20, TimeUnit.SECONDS), healthFirst || "INCIDENT".equals(other) ? 201 : 200);
            body(second.get(20, TimeUnit.SECONDS), 409);
            assertThat(count("vital_sign", id)).isEqualTo(healthFirst ? 4 : 0);
            assertThat(jdbc.queryForObject("select version from visit where id=?", Integer.class, id)).isEqualTo(2);
            if (healthFirst && "INCIDENT".equals(other)) {
                var refreshed = new java.util.HashMap<>(otherInput); refreshed.put("expectedVersion", 2);
                body(b.post(otherPath, refreshed), 201);
                assertThat(jdbc.queryForObject("select health_note from visit where id=?", String.class, id)).isEqualTo("Synthetic caregiver observation");
            } else if (!healthFirst && "INCIDENT".equals(other)) {
                assertThat(b.post(path(id), health(2)).statusCode()).isEqualTo(409);
            }
        }
    }
}
