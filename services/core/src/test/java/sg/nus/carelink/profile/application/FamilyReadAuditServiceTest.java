package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.FamilyReadAudit.Resource;
import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry.Outcome;
import sg.nus.carelink.shared.error.AuditUnavailable;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * Verifies family read attribution, failure outcomes and fail-closed audit writes.
 *
 * @author Wang Zhili
 */
class FamilyReadAuditServiceTest {

	private static final String USERNAME = "family-a";
	private static final Long ACCOUNT_ID = 1101L;
	private final UserDirectory users = mock(UserDirectory.class);
	private final AccessAudit audit = mock(AccessAudit.class);
	private final FamilyReadAudit service = new FamilyReadAuditService(users, audit, new FamilyReadTransaction());

	@ParameterizedTest
	@CsvSource({
			"ELDERS, ELDER, FM02_LIST_ELDERS",
			"VISITS, VISIT, FM02_LIST_VISITS",
			"CAREGIVER, CAREGIVER, FM02_READ_CAREGIVER",
			"CREDENTIALS, CAREGIVER_CREDENTIALS, FM02_LIST_CREDENTIALS"
	})
	void recordsTheAccountAndOperationWithoutCopyingTheResponse(Resource resource,
			String resourceType, String operation) {
		knownAccount(true, Role.FAMILY);
		var calls = new AtomicInteger();
		var response = List.of("private resident diagnosis", "private certificate number");
		String scope = "elderId=101;page=0;size=20";

		List<String> actual = service.read(USERNAME, resource, 201L, scope, () -> {
			calls.incrementAndGet();
			return response;
		});

		assertThat(actual).isSameAs(response);
		assertThat(calls.get()).isEqualTo(1);
		AccessAuditEntry entry = onlyEntry();
		assertThat(entry.actorUserId()).isEqualTo(ACCOUNT_ID);
		assertThat(entry.action()).isEqualTo("READ");
		assertThat(entry.resourceType()).isEqualTo(resourceType);
		assertThat(entry.resourceId()).isEqualTo(201L);
		assertThat(entry.outcome()).isEqualTo(Outcome.OK);
		assertThat(entry.detail()).contains(operation, scope)
				.doesNotContain("diagnosis", "certificate", USERNAME);
	}

	@Test
	void recordsAnEmptyCollectionAsASuccessfulReadWithoutAnIndividualResourceId() {
		knownAccount(true, Role.FAMILY);

		List<?> result = service.read(USERNAME, Resource.ELDERS, null, "", List::of);
		assertThat(result).isEmpty();

		AccessAuditEntry entry = onlyEntry();
		assertThat(entry.resourceId()).isNull();
		assertThat(entry.outcome()).isEqualTo(Outcome.OK);
		assertThat(entry.action()).isEqualTo("READ");
	}

	@ParameterizedTest
	@MethodSource("queryFailures")
	void recordsTheFailureOnceAndRethrowsTheOriginalQueryException(RuntimeException failure, Outcome outcome) {
		knownAccount(true, Role.FAMILY);

		assertThatThrownBy(() -> service.read(USERNAME, Resource.CAREGIVER, 201L, "", () -> {
			throw failure;
		})).isSameAs(failure);

		AccessAuditEntry entry = onlyEntry();
		assertThat(entry.actorUserId()).isEqualTo(ACCOUNT_ID);
		assertThat(entry.resourceId()).isEqualTo(201L);
		assertThat(entry.outcome()).isEqualTo(outcome);
		assertThat(entry.detail()).doesNotContain(failure.getMessage());
	}

	@ParameterizedTest
	@CsvSource({ "false, FAMILY", "true, MANAGER" })
	void retainsTheKnownActorForDisabledOrWrongRoleAccounts(boolean enabled, Role role) {
		knownAccount(enabled, role);
		var denied = new AccessDeniedException("private reason for access refusal");
		var calls = new AtomicInteger();

		assertThatThrownBy(() -> service.read(USERNAME, Resource.VISITS, null, "", () -> {
			calls.incrementAndGet();
			throw denied;
		})).isSameAs(denied);

		assertThat(calls.get()).isEqualTo(1);
		AccessAuditEntry entry = onlyEntry();
		assertThat(entry.actorUserId()).isEqualTo(ACCOUNT_ID);
		assertThat(entry.outcome()).isEqualTo(Outcome.DENIED);
	}

	@Test
	void auditsActorLookupFailureWithoutExecutingTheProtectedQuery() {
		var failure = new DataAccessResourceFailureException("private database connection details");
		when(users.findByUsername(USERNAME)).thenThrow(failure);
		var calls = new AtomicInteger();

		assertThatThrownBy(() -> service.read(USERNAME, Resource.ELDERS, null, "", () -> {
			calls.incrementAndGet();
			return "protected result";
		})).isSameAs(failure);

		assertThat(calls.get()).isZero();
		AccessAuditEntry entry = onlyEntry();
		assertThat(entry.actorUserId()).isNull();
		assertThat(entry.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(entry.detail()).doesNotContain(failure.getMessage());
	}

	@Test
	void recordsAnUnresolvedActorWithoutReplacingTheBusinessAuthorizationCheck() {
		when(users.findByUsername(USERNAME)).thenReturn(Optional.empty());
		var denied = new AccessDeniedException("private missing account context");
		var calls = new AtomicInteger();

		assertThatThrownBy(() -> service.read(USERNAME, Resource.CAREGIVER, 201L, "", () -> {
			calls.incrementAndGet();
			throw denied;
		})).isSameAs(denied);

		assertThat(calls.get()).isEqualTo(1);
		AccessAuditEntry entry = onlyEntry();
		assertThat(entry.actorUserId()).isNull();
		assertThat(entry.outcome()).isEqualTo(Outcome.DENIED);
		assertThat(entry.detail()).doesNotContain(USERNAME, denied.getMessage());
	}

	@Test
	void doesNotReturnTheResultOrRetryTheInsertWhenAuditCommitFails() {
		knownAccount(true, Role.FAMILY);
		var failure = new TransactionSystemException("private audit connection details");
		doThrow(failure).when(audit).append(any(AccessAuditEntry.class));
		var calls = new AtomicInteger();

		assertThatThrownBy(() -> service.read(USERNAME, Resource.CREDENTIALS, 201L, "", () -> {
			calls.incrementAndGet();
			return "private protected response";
		})).isInstanceOf(AuditUnavailable.class)
				.hasMessageNotContaining(failure.getMessage())
				.hasMessageNotContaining("private protected response");

		assertThat(calls.get()).isEqualTo(1);
		assertThat(onlyEntry().outcome()).isEqualTo(Outcome.OK);
	}

	@ParameterizedTest
	@MethodSource("queryFailures")
	void reportsAuditUnavailabilityInsteadOfPretendingTheFailedReadWasRecorded(RuntimeException failure,
			Outcome outcome) {
		knownAccount(true, Role.FAMILY);
		var auditFailure = new TransactionSystemException("private audit connection details");
		doThrow(auditFailure).when(audit).append(any(AccessAuditEntry.class));

		assertThatThrownBy(() -> service.read(USERNAME, Resource.CAREGIVER, 201L, "", () -> {
			throw failure;
		})).isInstanceOf(AuditUnavailable.class)
				.hasCause(auditFailure)
				.hasSuppressedException(failure)
				.hasMessageNotContaining(failure.getMessage())
				.hasMessageNotContaining("private audit connection details");

		assertThat(onlyEntry().outcome()).isEqualTo(outcome);
	}

	@Test
	void reportsTheAuditFailureOperationallyWithoutLoggingItsSensitiveMessageOrStack() {
		knownAccount(true, Role.FAMILY);
		doThrow(new TransactionSystemException("private database password"))
				.when(audit).append(any(AccessAuditEntry.class));
		Logger logger = (Logger) LoggerFactory.getLogger(FamilyReadAuditService.class);
		var appender = new ListAppender<ILoggingEvent>();
		appender.start();
		logger.addAppender(appender);
		try {
			assertThatThrownBy(() -> service.read(USERNAME, Resource.CAREGIVER, 201L, "", () -> "private result"))
					.isInstanceOf(AuditUnavailable.class);

			assertThat(appender.list).hasSize(1);
			ILoggingEvent event = appender.list.getFirst();
			assertThat(event.getFormattedMessage()).contains("1101", "CAREGIVER", "201", "OK",
					"TransactionSystemException").doesNotContain("private database password", "private result");
			assertThat(event.getThrowableProxy()).isNull();
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
		onlyEntry();
	}

	private void knownAccount(boolean enabled, Role role) {
		when(users.findByUsername(USERNAME)).thenReturn(Optional.of(
				new AppUser(ACCOUNT_ID, USERNAME, "Family account", Set.of(role), enabled)));
	}

	private AccessAuditEntry onlyEntry() {
		var captured = ArgumentCaptor.forClass(AccessAuditEntry.class);
		verify(audit).append(captured.capture());
		verifyNoMoreInteractions(audit);
		return captured.getValue();
	}

	private static Stream<Arguments> queryFailures() {
		return Stream.of(
				Arguments.of(new AccessDeniedException("private access denial context"), Outcome.DENIED),
				Arguments.of(new ResourceNotFound("private missing resource context", 201L), Outcome.FAILED),
				Arguments.of(new IllegalArgumentException("private rejected filter value"), Outcome.FAILED),
				Arguments.of(new DataAccessResourceFailureException("private query database details"), Outcome.FAILED));
	}
}
