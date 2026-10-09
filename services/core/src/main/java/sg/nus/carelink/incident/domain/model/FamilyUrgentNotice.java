package sg.nus.carelink.incident.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** A safe in-app message and a proposed first response deadline, never a manager deadline. @author Wang Zhili */
public record FamilyUrgentNotice(String title, String body, LocalDateTime createdAt, LocalDateTime acknowledgeBy) {
	public static FamilyUrgentNotice forIncident(FamilyAlertEvent event, Incident incident, LocalDateTime now, Duration window) {
		if (window.isNegative() || window.isZero()) { throw new IllegalArgumentException("The family response window must be positive"); }
		boolean unresolved = event.type() == FamilyAlertEvent.Type.INCIDENT_UNRESOLVED;
		String title = unresolved ? "Urgent care alert: incident not taken up" : "Urgent care alert: " + incident.severity();
		String body = unresolved ? "The institution has been unable to assign a responder. Open the incident details."
				: "A " + incident.category() + " incident has been reported. Open the incident details.";
		return new FamilyUrgentNotice(title, body, now, now.plus(window));
	}
}
