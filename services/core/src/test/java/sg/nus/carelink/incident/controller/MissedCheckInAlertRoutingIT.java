package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.testsupport.VisitStandIn;

/**
 * SYS03 alerts the caregiver on the visit that missed its check-in, never the caregiver on the elder's
 * latest visit. visit raises the incident through core's internal API, as these tests do; the caregiver's
 * own system-alert reads are visit's, and CaregiverSystemAlertIT there covers them.
 */
class MissedCheckInAlertRoutingIT extends CaregiverHttpITSupport {

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, MissedCheckInAlertRoutingIT.class, null);
    }

    @BeforeEach void clearPreviousScenarios() {
        for (String table : List.of("family_alert_window", "family_alert_delivery", "family_alert_event", "requested_notification",
                "incident_acknowledgement", "incident_log", "incident")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private long missedCheckIn() {
        var due = day.atTime(9, 15);
        return internalApi().raiseMissedCheckIn(new CoreApi.MissedCheckInRequest(elder, visit, due, due.plusMinutes(1),
                "missed-check-in:" + visit + ":" + due)).incidentId();
    }

    private long raised(long recipient, long incident) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM requested_notification WHERE recipient_user_id=? AND resource_id=? AND event_type='INCIDENT_RAISED'",
                Long.class, recipient, incident);
    }

    @Test void alertGoesToTheTriggeringVisitsCaregiverNotTheLatestVisitsAndFamilyHearsOnce() {
        // A later visit to the same elder belongs to B, so B is the elder's "latest" caregiver.
        visits.add(VisitStandIn.Held.scheduled(elder, otherCaregiver, day.plusDays(1).atTime(9, 0), day.plusDays(1).atTime(10, 0)));
        long otherUser = jdbc.queryForObject("SELECT user_id FROM caregiver WHERE id=?", Long.class, otherCaregiver);

        long incident = missedCheckIn();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requested_notification WHERE recipient_user_id=? AND resource_id=? AND event_type='INCIDENT_RAISED' AND resource_type='CAREGIVER_SYSTEM_ALERT'",
                Long.class, caregiverUser, incident)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT body FROM requested_notification WHERE recipient_user_id=? AND resource_id=?",
                String.class, caregiverUser, incident)).contains("Visit " + visit).doesNotContain("Demo Elder");
        assertThat(raised(otherUser, incident)).isZero();
        assertThat(raised(manager, incident)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?", Long.class, incident)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_log WHERE incident_id=? AND action='BROADCAST' AND detail LIKE '%assigned to the triggering visit%'",
                Long.class, incident)).isEqualTo(1);
    }

    @Test void aCaregiverWhoIsAlsoAManagerKeepsTheManagersIncidentLinkWithoutADuplicate() {
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'MANAGER')", caregiverUser);

        long incident = missedCheckIn();

        assertThat(raised(caregiverUser, incident)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT resource_type FROM requested_notification WHERE recipient_user_id=? AND resource_id=? AND event_type='INCIDENT_RAISED'",
                String.class, caregiverUser, incident)).isEqualTo("INCIDENT");
    }

    @Test void aDisabledCaregiverIsNotAlerted() {
        jdbc.update("UPDATE app_user SET enabled=false WHERE id=?", caregiverUser);

        long incident = missedCheckIn();

        assertThat(raised(caregiverUser, incident)).isZero();
        assertThat(raised(manager, incident)).isEqualTo(1);
    }

}
