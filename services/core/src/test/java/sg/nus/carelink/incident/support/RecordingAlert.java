package sg.nus.carelink.incident.support;

import java.util.ArrayList;
import java.util.List;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentAlert;

/**
 * Records who would have been told, so a test can assert on the broadcast without a
 * database, a mail server or a push provider.
 */
public final class RecordingAlert implements IncidentAlert {

	public record HandOver(Long incidentId, Long from, Long to) {
	}

	private final List<Long> broadcasts = new ArrayList<>();
	private final List<HandOver> handOvers = new ArrayList<>();
	private final List<Long> exhausted = new ArrayList<>();
	private int audienceSize = 3;

	public RecordingAlert withAudienceOf(int size) {
		this.audienceSize = size;
		return this;
	}

	@Override
	public int broadcastRaised(Incident incident) {
		broadcasts.add(incident.id());
		return audienceSize;
	}

	@Override
	public void handedOver(Incident incident, Long fromUserId, Long toUserId) {
		handOvers.add(new HandOver(incident.id(), fromUserId, toUserId));
	}

	@Override
	public void chainExhausted(Incident incident) {
		exhausted.add(incident.id());
	}

	public List<Long> broadcasts() {
		return List.copyOf(broadcasts);
	}

	public List<HandOver> handOvers() {
		return List.copyOf(handOvers);
	}

	public List<Long> exhausted() {
		return List.copyOf(exhausted);
	}
}
