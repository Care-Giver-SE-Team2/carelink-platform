package sg.nus.carelink.visit.controller;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.testsupport.MissedCheckInITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.application.VisitReassignment;
import static org.assertj.core.api.Assertions.*;

/**
 * The caregiver's own view of the missed check-ins raised on their visits. Who core alerts, and the
 * family's copy, are core's: MissedCheckInAlertRoutingIT there covers them.
 */
class CaregiverSystemAlertIT extends MissedCheckInITSupport {
    @Autowired VisitReassignment changes;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry,CaregiverSystemAlertIT.class,"+05:00","connectionTimeZone=Asia/Singapore"); }
    @Test void triggeringCaregiverSeesOwnMinimalAlertAndLaterVisitsCaregiverDoesNot() throws Exception {
        long id=plannedVisit();
        // The fixture already has a much later visit. Give that unrelated visit to B.
        changes.reassign(visit,otherCaregiver,new VisitReassignment.Change(null,manager,null,"Separate future visit"));
        overdue(); assertThat(scan.trigger(id)).isTrue(); long event=incident(id);
        assertThat(scan.trigger(id)).isFalse();
        try(var cg=browser(caregiverName);var other=browser(otherName)) {
            var detail=cg.read("/api/caregivers/me/system-alerts/"+event);
            assertThat(detail.path("visitId").asLong()).isEqualTo(id);
            assertThat(detail.path("source").asString()).isEqualTo("SYSTEM_MISSED_CHECKIN");
            assertThat(detail.path("workPackAvailable").asBoolean()).isTrue();
            assertThat(detail.toString()).doesNotContain("elderId","fullName","description","address","resolutionNote");
            assertThat(other.get("/api/caregivers/me/system-alerts/"+event).statusCode()).isEqualTo(404);
            assertThat(other.read("/api/caregivers/me/system-alerts").path("total").asLong()).isZero();
        }
    }
    @Test void reassignmentRetainsOnlyOriginalCaregiversMinimalSummary() throws Exception {
        long id=plannedVisit(); overdue(); assertThat(scan.trigger(id)).isTrue(); long event=incident(id);
        changes.reassign(id,otherCaregiver,new VisitReassignment.Change(null,manager,null,"Manager changed assignment"));
        try(var original=browser(caregiverName);var replacement=browser(otherName)) {
            var detail=original.read("/api/caregivers/me/system-alerts/"+event);
            assertThat(detail.path("workPackAvailable").asBoolean()).isFalse();
            assertThat(detail.toString()).doesNotContain("elderId","caregiverId","fullName","description");
            assertThat(original.get("/api/visits/"+id+"/work-pack").statusCode()).isEqualTo(403);
            assertThat(replacement.get("/api/caregivers/me/system-alerts/"+event).statusCode()).isEqualTo(404);
        }
    }
    @Test void applicantAwaitingAdmissionCannotReadSystemAlerts() throws Exception {
        jdbc.update("update caregiver set status='ONBOARDING' where id=?",caregiver);
        try(var cg=browser(caregiverName)) {
            assertThat(cg.get("/api/caregivers/me/system-alerts").statusCode()).isEqualTo(403);
        }
    }
    @Test void cancelledVisitCannotBeOpenedAndPaginationIsBounded() throws Exception {
        long id=plannedVisit(); overdue(); assertThat(scan.trigger(id)).isTrue(); long event=incident(id);
        changes.callOff(id,new VisitReassignment.Change(null,manager,null,"Cancelled"));
        try(var cg=browser(caregiverName)) {
            assertThat(cg.read("/api/caregivers/me/system-alerts/"+event).path("workPackAvailable").asBoolean()).isFalse();
            assertThat(cg.get("/api/caregivers/me/system-alerts?size=500").statusCode()).isEqualTo(409);
        }
    }
}
