package sg.nus.carelink.profile.application;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes a family query transaction before its independent audit write begins.
 *
 * @author Wang Zhili
 */
@Service
class FamilyReadTransaction {

	@Transactional(readOnly = true)
	public <T> T execute(Supplier<T> query) {
		return query.get();
	}
}
