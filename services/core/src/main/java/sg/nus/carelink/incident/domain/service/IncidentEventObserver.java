package sg.nus.carelink.incident.domain.service;

import sg.nus.carelink.incident.domain.model.FamilyAlertEvent;

/** Observer of committed care incident events. @author Wang Zhili */
@FunctionalInterface
public interface IncidentEventObserver {
	void onIncidentEvent(FamilyAlertEvent event);
}
