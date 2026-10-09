package sg.nus.carelink.incident.domain.service;

import java.util.ArrayList;
import java.util.List;
import sg.nus.carelink.incident.domain.model.FamilyAlertEvent;

/** The subject knows observer interfaces, never family selection or notification storage. @author Wang Zhili */
public class IncidentEventSubject {
	private final List<IncidentEventObserver> observers;

	public IncidentEventSubject(List<IncidentEventObserver> observers) { this.observers = List.copyOf(observers); }

	public List<RuntimeException> notifyObservers(FamilyAlertEvent event) {
		var failures = new ArrayList<RuntimeException>();
		for (var observer : observers) {
			try { observer.onIncidentEvent(event); }
			catch (RuntimeException failure) { failures.add(failure); }
		}
		return List.copyOf(failures);
	}
}
