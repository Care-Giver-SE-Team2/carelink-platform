package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.visit.domain.repository.VisitRequestKeys;

/**
 * Makes a change to a visit once per request key, for the callers that send a change again when they
 * did not hear back: core's re-rostering retries with the same key after a timeout, and gets the answer
 * the first request got - the visit a move created, say - instead of a second change, or a refusal of a
 * change already made. The change and its key commit together. A request without a key changes the
 * visit every time, as before.
 */
@Service
public class VisitRequests {

	private final VisitRequestKeys keys;
	private final TransactionTemplate transaction;
	private final Clock clock;

	VisitRequests(VisitRequestKeys keys, PlatformTransactionManager transactions, Clock clock) {
		this.keys = keys;
		this.transaction = new TransactionTemplate(transactions);
		this.clock = clock;
	}

	/**
	 * What the key's request did before, or what {@code change} does now.
	 *
	 * @param change makes the change and answers the visit it created, or null when it created none; it runs
	 *     in the transaction that remembers the key
	 * @return the visit the change created (a move's new visit), or null
	 */
	@SuppressWarnings("java:S4276") // a change that creates no visit answers null, which a LongSupplier cannot
	public Long once(String requestKey, Long visitId, Supplier<Long> change) {
		if (requestKey == null || requestKey.isBlank()) {
			return transaction.execute(status -> change.get());
		}
		try {
			return transaction.execute(status -> {
				Optional<VisitRequestKeys.Made> earlier = keys.find(requestKey);
				if (earlier.isPresent()) {
					return earlier.get().answerFor(visitId);
				}
				Long created = change.get();
				keys.remember(new VisitRequestKeys.Made(requestKey, visitId, created), LocalDateTime.now(clock));
				return created;
			});
		}
		catch (DuplicateKeyException | OptimisticLockingFailureException sameRequestAtTheSameTime) {
			// The same request, sent twice at once: the other one's change and key committed, and this one
			// lost on the key or on the visit's version and rolled back
			Optional<VisitRequestKeys.Made> made = transaction.execute(status -> keys.find(requestKey));
			return made.orElseThrow(() -> sameRequestAtTheSameTime).answerFor(visitId);
		}
	}
}
