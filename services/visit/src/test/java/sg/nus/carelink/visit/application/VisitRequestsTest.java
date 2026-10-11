package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.visit.domain.repository.VisitRequestKeys;

/**
 * A keyed change is made once: sent again it gets the first answer, also when the two arrive at the same
 * moment and the second loses on the key or on the visit's version. Without a key nothing is remembered.
 * The transactions are a mock's; VisitApiIT runs the same against MySQL.
 */
class VisitRequestsTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T01:00:00Z"), ZoneId.of("Asia/Singapore"));

	private final Keys keys = new Keys();

	private final VisitRequests requests = new VisitRequests(keys, mock(PlatformTransactionManager.class), CLOCK);

	private final AtomicInteger changes = new AtomicInteger();

	@Test
	void aKeyedChangeIsMadeOnceAndTheRepeatGetsItsAnswer() {
		assertThat(requests.once("step-1", 100L, this::move)).isEqualTo(501L);
		assertThat(requests.once("step-1", 100L, this::move)).isEqualTo(501L);
		assertThat(requests.once("step-2", 100L, this::handOver)).isNull();
		assertThat(requests.once("step-2", 100L, this::handOver)).isNull();

		assertThat(changes).hasValue(2);
		assertThat(keys.rememberedAt).containsEntry("step-1", LocalDateTime.of(2026, 10, 11, 9, 0));
	}

	@Test
	void withoutAKeyTheChangeIsMadeEveryTime() {
		requests.once(null, 100L, this::handOver);
		requests.once(" ", 100L, this::handOver);

		assertThat(changes).hasValue(2);
		assertThat(keys.made).isEmpty();
	}

	@Test
	void theSameRequestAtTheSameTimeGetsTheAnswerOfTheOneThatCommitted() {
		keys.otherRequestCommits = new VisitRequestKeys.Made("step-3", 100L, 777L);

		assertThat(requests.once("step-3", 100L, this::move)).isEqualTo(777L);

		keys.otherRequestCommits = new VisitRequestKeys.Made("step-4", 100L, null);
		keys.loseOnTheVersion = true;
		assertThat(requests.once("step-4", 100L, this::handOver)).isNull();
	}

	@Test
	void aConflictWithNoRequestBehindItIsPassedOn() {
		keys.loseOnTheVersion = true;

		assertThatThrownBy(() -> requests.once("step-5", 100L, this::handOver))
				.isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void aKeySentWithAnotherVisitIsRefused() {
		requests.once("step-6", 100L, this::handOver);

		assertThatThrownBy(() -> requests.once("step-6", 101L, this::handOver))
				.isInstanceOfSatisfying(BusinessRuleViolation.class,
						refused -> assertThat(refused.code()).isEqualTo("REQUEST_KEY_REUSED"));
		assertThat(changes).hasValue(1);
	}

	private Long move() {
		changes.incrementAndGet();
		return 500L + changes.get();
	}

	private Long handOver() {
		changes.incrementAndGet();
		return null;
	}

	/** visit_request in memory; can play another request with the same key committing first. */
	private static final class Keys implements VisitRequestKeys {

		private final Map<String, Made> made = new HashMap<>();
		private final Map<String, LocalDateTime> rememberedAt = new HashMap<>();
		private Made otherRequestCommits;
		private boolean loseOnTheVersion;

		@Override
		public Optional<Made> find(String requestKey) {
			return Optional.ofNullable(made.get(requestKey));
		}

		@Override
		public void remember(Made request, LocalDateTime at) {
			if (loseOnTheVersion) {
				loseOnTheVersion = false;
				commitTheOtherRequest();
				throw new OptimisticLockingFailureException("visit changed concurrently");
			}
			if (otherRequestCommits != null) {
				commitTheOtherRequest();
				throw new DuplicateKeyException("Duplicate entry '" + request.requestKey() + "'");
			}
			made.put(request.requestKey(), request);
			rememberedAt.put(request.requestKey(), at);
		}

		private void commitTheOtherRequest() {
			if (otherRequestCommits != null) {
				made.put(otherRequestCommits.requestKey(), otherRequestCommits);
				otherRequestCommits = null;
			}
		}
	}
}
