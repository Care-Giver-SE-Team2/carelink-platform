package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;

/** A binding removed after audience enumeration cannot receive an alert. @author Wang Zhili */
class FamilyAlertRecipientsServiceTest {
	@Test void deletedBindingAndProfileAreExcludedWhenTheRecipientIsResolved() {
		var bindings = mock(ElderFamilyBindingRepository.class);
		var families = mock(FamilyMemberRepository.class);
		when(families.findById(42L)).thenReturn(Optional.empty());
		when(bindings.findByElderIdAndFamilyMemberId(101L, 42L)).thenReturn(Optional.empty());
		var service = new FamilyAlertRecipientsService(bindings, families, mock(UserDirectory.class), Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC));
		var candidate = service.resolve(101L, 42L);
		assertThat(candidate.exclusionReason()).isEqualTo("BINDING_MISSING");
		assertThat(candidate.userId()).isNull();
		assertThat(candidate.eligible()).isFalse();
	}
}
