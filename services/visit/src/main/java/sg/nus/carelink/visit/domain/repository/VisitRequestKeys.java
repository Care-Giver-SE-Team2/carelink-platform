package sg.nus.carelink.visit.domain.repository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Which change each keyed request made, so a request that arrives again - a retry after its answer was
 * lost - gets the same answer instead of a second change.
 */
public interface VisitRequestKeys {

	Optional<Made> find(String requestKey);

	/** Fails with a duplicate key when another request with the same key got there first. */
	void remember(Made made, LocalDateTime at);

	/**
	 * What a keyed request did.
	 *
	 * @param resultVisitId the visit a move created; null for the other changes
	 */
	record Made(String requestKey, Long visitId, Long resultVisitId) {

		public Made {
			Objects.requireNonNull(requestKey, "requestKey");
			Objects.requireNonNull(visitId, "visitId");
		}

		/**
		 * The answer for a request to change {@code visitId} with this key again. A key is one request's;
		 * sent with another visit it is a caller's mistake, refused rather than answered for the wrong visit.
		 */
		public Long answerFor(Long requestedVisitId) {
			if (!visitId.equals(requestedVisitId)) {
				throw new BusinessRuleViolation("REQUEST_KEY_REUSED",
						"Request key %s already changed visit %d, not visit %d".formatted(requestKey, visitId,
								requestedVisitId));
			}
			return resultVisitId;
		}
	}
}
