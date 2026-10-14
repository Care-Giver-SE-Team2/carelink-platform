package sg.nus.carelink.openplatform.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.openplatform.domain.model.ProviderApplication;

/** Every application to join the Open Platform, decided or not. */
public interface ProviderApplications {

	/** Keeps a new application and returns it with its id. */
	ProviderApplication create(ProviderApplication application);

	Optional<ProviderApplication> find(long id);

	/** The portal user's latest application, whatever its status. */
	Optional<ProviderApplication> latestOf(String portalUserId);

	/** Whether an application that holds this email (pending or approved) is kept. */
	boolean emailHeld(String contactEmail);

	/** The applications waiting for a manager, newest first. */
	List<ProviderApplication> pending();

	/**
	 * Moves a pending application to its decision, only if it is still pending.
	 *
	 * @return false when another manager decided it meanwhile, and nothing was written
	 */
	boolean decide(ProviderApplication decided);
}
