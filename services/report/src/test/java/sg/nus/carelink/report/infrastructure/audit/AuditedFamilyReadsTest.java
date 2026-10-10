package sg.nus.carelink.report.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import sg.nus.carelink.platform.security.SignedInUser;
import sg.nus.carelink.platform.security.SignedInUsers;
import sg.nus.carelink.report.application.FamilyReadAudit.Resource;
import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry.Outcome;
import sg.nus.carelink.shared.error.AuditUnavailable;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Every family read is recorded once, with the signed-in account, the resource and the outcome,
 * and never with the content; a read whose record cannot be written is not served.
 */
class AuditedFamilyReadsTest {

	private final SignedInUsers signedIn = mock(SignedInUsers.class);
	private final AccessAudit audit = mock(AccessAudit.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

	private final AuditedFamilyReads reads = new AuditedFamilyReads(signedIn, audit, transactions);

	AuditedFamilyReadsTest() {
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(signedIn.current()).thenReturn(Optional.of(new SignedInUser(7L, "family-a", "Family A", Set.of("FAMILY"))));
	}

	@Test
	void aServedReadIsRecordedAsOkWithItsScope() {
		String body = reads.read("family-a", Resource.WEEKLY_SUMMARY, 101L, "weekStart=2026-09-21", () -> "summary");

		assertThat(body).isEqualTo("summary");
		assertThat(recorded()).isEqualTo(new AccessAuditEntry(7L, "READ", "ELDER", 101L, Outcome.OK,
				"FM04_READ_WEEKLY_SUMMARY;weekStart=2026-09-21"));
	}

	@Test
	void aRefusedReadIsRecordedAsDeniedAndTheRefusalReachesTheReader() {
		assertThatThrownBy(() -> reads.read("family-a", Resource.REPORT_DETAIL, 310L, "", () -> {
			throw new AccessDeniedException("A readable FAMILY report is required");
		})).isInstanceOf(AccessDeniedException.class);

		assertThat(recorded()).isEqualTo(new AccessAuditEntry(7L, "READ", "REPORT", 310L, Outcome.DENIED,
				"FM04_READ_REPORT"));
	}

	@Test
	void aFailedReadIsRecordedAsFailed() {
		assertThatThrownBy(() -> reads.read("family-a", Resource.REPORT_DETAIL, 999L, "", () -> {
			throw new ResourceNotFound("Report", 999L);
		})).isInstanceOf(ResourceNotFound.class);

		assertThat(recorded().outcome()).isEqualTo(Outcome.FAILED);
	}

	@Test
	void anotherAccountsReadIsRecordedWithoutAnActor() {
		reads.read("family-b", Resource.REPORTS, null, "", () -> "page");

		assertThat(recorded().actorUserId()).isNull();
	}

	@Test
	void aReadWhoseRecordCannotBeWrittenIsNotServed() {
		doThrow(new IllegalStateException("audit_log is gone")).when(audit).append(any());

		assertThatThrownBy(() -> reads.read("family-a", Resource.REPORTS, null, "", () -> "page"))
				.isInstanceOf(AuditUnavailable.class);
		assertThatThrownBy(() -> reads.read("family-a", Resource.REPORTS, null, "", () -> {
			throw new AccessDeniedException("No binding");
		})).isInstanceOf(AuditUnavailable.class)
				.satisfies(unavailable -> assertThat(unavailable.getSuppressed()).hasOnlyElementsOfType(AccessDeniedException.class));
	}

	private AccessAuditEntry recorded() {
		ArgumentCaptor<AccessAuditEntry> entry = ArgumentCaptor.forClass(AccessAuditEntry.class);
		verify(audit).append(entry.capture());
		return entry.getValue();
	}

}
