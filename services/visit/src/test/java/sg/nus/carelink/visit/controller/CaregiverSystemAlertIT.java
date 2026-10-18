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

class CaregiverSystemAlertIT extends MissedCheckInITSupport {
    @Autowired VisitReassignment changes;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry,CaregiverSystemAlertIT.class,"+05:00","connectionTimeZone=Asia/Singapore"); }
    @Test void notificationUsesTheTriggeringVisitNotTheMostRecentVisitAndRoutesFamilyOnlyOnce() throws Exception {
        long id=plannedVisit();
        // The fixture already has a much later visit. Give that unrelated visit to B.
        changes.reassign(visit,otherCaregiver,new VisitReassignment.Change(null,manager,null,"Separate future visit"));
        overdue(); assertThat(scan.trigger(id)).isTrue(); long event=incident(id);
        long otherUser=jdbc.queryForObject("select user_id from caregiver where id=?",Long.class,otherCaregiver);
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where JSON_EXTRACT(payload,'$.recipientUserId')=? and JSON_EXTRACT(payload,'$.resourceId')=? and type='NotificationRequested' and JSON_UNQUOTE(JSON_EXTRACT(payload,'$.resourceType'))='CAREGIVER_SYSTEM_ALERT'",Long.class,caregiverUser,event)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where JSON_EXTRACT(payload,'$.recipientUserId')=? and JSON_EXTRACT(payload,'$.resourceId')=? and type='NotificationRequested'",Long.class,otherUser,event)).isZero();
        // Core family-alert delivery is covered separately; visit cannot read core or notification tables.
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
    @Test void multiRoleManagerKeepsExistingManagerIncidentLinkWithoutDuplicateNotification() throws Exception {
        long id=plannedVisit();
        jdbc.update("insert into user_role(user_id,role) values (?,'MANAGER')",caregiverUser);
        overdue(); assertThat(scan.trigger(id)).isTrue(); long event=incident(id);
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where JSON_EXTRACT(payload,'$.recipientUserId')=? and JSON_EXTRACT(payload,'$.resourceId')=? and type='NotificationRequested'",Long.class,caregiverUser,event)).isZero(); // core alone publishes the multi-role manager message
        assertThat(core.enabledAccountsWithRole("MANAGER").userIds()).contains(caregiverUser);
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
