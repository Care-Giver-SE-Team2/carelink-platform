package sg.nus.carelink.incident.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.Responder;
import sg.nus.carelink.incident.domain.repository.ManagerDirectory;

/**
 * A manager directory a test can state in one line.
 *
 * <p>This is the payoff of {@code ManagerDirectory} being an interface: the whole escalation
 * chain, including "this institution has no managers at all" and "every manager has already
 * had it", is exercised without a database or a container.
 */
public final class FakeManagerDirectory implements ManagerDirectory {

	private final List<Responder> managers = new ArrayList<>();

	public static FakeManagerDirectory with(Responder... responders) {
		FakeManagerDirectory directory = new FakeManagerDirectory();
		directory.managers.addAll(List.of(responders));
		return directory;
	}

	/** Nobody at all. Drives the "chain exhausted" branch. */
	public static FakeManagerDirectory empty() {
		return new FakeManagerDirectory();
	}

	@Override
	public List<Responder> allManagers() {
		return List.copyOf(managers);
	}

	@Override
	public Optional<Responder> responderById(Long userId) {
		return managers.stream().filter(manager -> manager.userId().equals(userId)).findFirst();
	}
}
