package sg.nus.carelink.profile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.shared.security.Role;

/** Creates an ELDER login and the minimum linked elder record in one transaction. */
@Service
public class ElderAccountRegistrationService {
    private final AccountIssuer accounts;
    private final ElderRepository elders;

    public ElderAccountRegistrationService(AccountIssuer accounts, ElderRepository elders) {
        this.accounts = accounts;
        this.elders = elders;
    }

    @Transactional
    public Long register(String fullName, String username, String password) {
        Long userId = accounts.register(username, fullName, password, Role.ELDER);
        // Only the name is asked at sign-up; family members may add the other details later.
        elders.save(new Elder(null, userId, fullName, null, null, null, null,
                null, null, null, null, null, Elder.ContinuityPreference.PREFERRED,
                null, null, null));
        return userId;
    }
}
