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
    public Long register(String username, String password) {
        Long userId = accounts.register(username, username, password, Role.ELDER);
        // full_name is NOT NULL in the current schema. The username is a temporary label,
        // not a verified personal name. Family members may update the profile later.
        elders.save(new Elder(null, userId, username, null, null, null, null,
                null, null, null, null, null, Elder.ContinuityPreference.PREFERRED,
                null, null, null));
        return userId;
    }
}
