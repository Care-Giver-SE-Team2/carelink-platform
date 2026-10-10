package sg.nus.carelink.profile.application;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.security.Role;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ElderAccountRegistrationServiceTest {
    private final AccountIssuer accounts = mock(AccountIssuer.class);
    private final ElderRepository elders = mock(ElderRepository.class);
    private final ElderAccountRegistrationService service = new ElderAccountRegistrationService(accounts, elders);

    @Test
    void registersLoginAndMinimumElderProfileUnderTheChosenName() {
        when(accounts.register("elder.new", "Tan Ah Mah", "password123", Role.ELDER)).thenReturn(42L);
        when(elders.save(any(Elder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.register("Tan Ah Mah", "elder.new", "password123")).isEqualTo(42L);

        var elderCaptor = org.mockito.ArgumentCaptor.forClass(Elder.class);
        verify(elders).save(elderCaptor.capture());
        Elder elder = elderCaptor.getValue();
        assertThat(elder.id()).isNull();
        assertThat(elder.userId()).isEqualTo(42L);
        assertThat(elder.fullName()).isEqualTo("Tan Ah Mah");
        assertThat(elder.continuityPreference()).isEqualTo(Elder.ContinuityPreference.PREFERRED);
        assertThat(elder.phone()).isNull();
        assertThat(elder.address()).isNull();
        assertThat(elder.dateOfBirth()).isNull();
        assertThat(elder.medicalNotes()).isNull();
    }

    @Test
    void duplicateUsernameDoesNotCreateElderProfile() {
        when(accounts.register(any(), any(), any(), eq(Role.ELDER)))
                .thenThrow(new BusinessRuleViolation("USERNAME_TAKEN", "That username is already taken"));

        assertThatThrownBy(() -> service.register("Tan Ah Mah", "taken", "password123"))
                .isInstanceOf(BusinessRuleViolation.class);
        verifyNoInteractions(elders);
    }

    @Test
    void propagatesProfilePersistenceFailureForTransactionalRollback() {
        when(accounts.register("elder.new", "Tan Ah Mah", "password123", Role.ELDER)).thenReturn(42L);
        when(elders.save(any(Elder.class))).thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> service.register("Tan Ah Mah", "elder.new", "password123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database failure");
    }
}
