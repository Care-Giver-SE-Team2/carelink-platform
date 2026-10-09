package sg.nus.carelink.incident.support;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import sg.nus.carelink.incident.application.IncidentFamilyEvents;
import sg.nus.carelink.incident.domain.model.FamilyAlertEvent;

/** Records source facts without Spring transactions in application unit tests. @author Wang Zhili */
public final class RecordingIncidentFamilyEvents implements IncidentFamilyEvents {
    private final List<FamilyAlertEvent> events = new ArrayList<>();

    @Override public void raised(UUID id, Long incident, Long elder, OffsetDateTime at) {
        events.add(new FamilyAlertEvent(id, FamilyAlertEvent.Type.INCIDENT_RAISED, incident, elder, at));
    }
    @Override public void unresolved(UUID id, Long incident, Long elder, OffsetDateTime at) {
        events.add(new FamilyAlertEvent(id, FamilyAlertEvent.Type.INCIDENT_UNRESOLVED, incident, elder, at));
    }
    public List<FamilyAlertEvent> events() { return List.copyOf(events); }
}
