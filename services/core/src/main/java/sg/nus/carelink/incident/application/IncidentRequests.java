package sg.nus.carelink.incident.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.incident.domain.repository.IncidentRequestKeys;

/**
 * Raises an incident once per request key, for the services that ask core to raise one inside their own
 * transaction: if their transaction then fails, they ask again with the same key and get the incident
 * that is already there, instead of a second one. The incident and its key commit together. A request
 * without a key raises an incident every time.
 */
@Service
public class IncidentRequests {

	private final IncidentRequestKeys keys;
	private final TransactionTemplate transaction;
	private final Clock clock;

	IncidentRequests(IncidentRequestKeys keys, PlatformTransactionManager transactions, Clock clock) {
		this.keys = keys;
		this.transaction = new TransactionTemplate(transactions);
		this.clock = clock;
	}

	/**
	 * The incident the key raised before, or the one {@code raise} raises now.
	 *
	 * @param raise raises the incident and answers its id; it runs in the transaction that remembers the key
	 */
	public Long once(String requestKey, Supplier<Long> raise) {
		if (requestKey == null || requestKey.isBlank()) {
			return transaction.execute(status -> raise.get());
		}
		try {
			return transaction.execute(status -> keys.incidentFor(requestKey).orElseGet(() -> {
				Long incidentId = raise.get();
				keys.remember(requestKey, incidentId, LocalDateTime.now(clock));
				return incidentId;
			}));
		}
		catch (DuplicateKeyException sameRequestAtTheSameTime) {
			// The same request, sent twice at once: the other one's incident committed, and this one's rolled back
			Optional<Long> raised = transaction.execute(status -> keys.incidentFor(requestKey));
			return raised.orElseThrow(() -> sameRequestAtTheSameTime);
		}
	}
}
