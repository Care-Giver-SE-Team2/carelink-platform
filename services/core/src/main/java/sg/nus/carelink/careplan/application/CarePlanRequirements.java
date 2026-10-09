package sg.nus.carelink.careplan.application;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Cross-module contract for UC-MG06: which certifications a caregiver must hold to work on a
 * plan's visits, so rostering can tell which visits a lapsing certificate puts at risk.
 * Rostering imports this interface only, never careplan's domain or infrastructure.
 */
public interface CarePlanRequirements {

	/**
	 * The credential type ids each of these plans requires. A plan that requires none is
	 * absent from the map rather than mapped to an empty set.
	 */
	Map<Long, Set<Long>> requiredCredentialTypes(Collection<Long> carePlanIds);
}
