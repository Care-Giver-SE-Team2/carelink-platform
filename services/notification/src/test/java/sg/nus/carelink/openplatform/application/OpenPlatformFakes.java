package sg.nus.carelink.openplatform.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.openplatform.domain.model.InvalidField;
import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.repository.ProviderApplications;
import sg.nus.carelink.openplatform.domain.repository.Providers;
import sg.nus.carelink.openplatform.domain.repository.WebhookEndpoints;

/**
 * The Open Platform's stores and neighbours in memory, with the JDBC adapters' rules: a decision only on
 * a pending application, a change only from the state read. The identities are maps; sealing a secret
 * marks it, so a test can see what was sealed and that nothing else was.
 */
final class OpenPlatformFakes {

	private OpenPlatformFakes() {
	}

	static final class Applications implements ProviderApplications {

		final Map<Long, ProviderApplication> byId = new LinkedHashMap<>();
		private long nextId = 417;

		@Override
		public ProviderApplication create(ProviderApplication application) {
			ProviderApplication kept = new ProviderApplication(nextId++, application.details(),
					application.portalUserId(), application.status(), application.reason(), application.submittedAt(),
					application.reviewedAt(), application.reviewedBy(), application.providerId());
			byId.put(kept.id(), kept);
			return kept;
		}

		@Override
		public Optional<ProviderApplication> find(long id) {
			return Optional.ofNullable(byId.get(id));
		}

		@Override
		public Optional<ProviderApplication> latestOf(String portalUserId) {
			return byId.values().stream()
					.filter(application -> application.portalUserId().equals(portalUserId))
					.max(Comparator.comparing(ProviderApplication::submittedAt).thenComparing(ProviderApplication::id));
		}

		@Override
		public boolean emailHeld(String contactEmail) {
			return byId.values().stream().anyMatch(application -> application.holdsItsEmail()
					&& application.details().contactEmail().equals(contactEmail));
		}

		@Override
		public List<ProviderApplication> pending() {
			return byId.values().stream()
					.filter(application -> application.status() == ProviderApplication.Status.PENDING)
					.sorted(Comparator.comparing(ProviderApplication::submittedAt).reversed())
					.toList();
		}

		@Override
		public boolean decide(ProviderApplication decided) {
			if (byId.get(decided.id()).status() != ProviderApplication.Status.PENDING) {
				return false;
			}
			byId.put(decided.id(), decided);
			return true;
		}
	}

	static final class ProviderStore implements Providers {

		final Map<Long, Provider> byId = new LinkedHashMap<>();

		@Override
		public Provider create(Provider provider) {
			if (byId.containsKey(provider.id())) {
				throw new IllegalStateException("Provider " + provider.id() + " exists");
			}
			byId.put(provider.id(), provider);
			return provider;
		}

		@Override
		public Optional<Provider> find(long id) {
			return Optional.ofNullable(byId.get(id));
		}

		@Override
		public Optional<Provider> findByPortalUser(String portalUserId) {
			return byId.values().stream().filter(provider -> provider.portalUserId().equals(portalUserId)).findFirst();
		}

		@Override
		public List<Provider> all() {
			return byId.values().stream().sorted(Comparator.comparing(Provider::approvedAt).reversed()).toList();
		}

		@Override
		public boolean update(Provider before, Provider after) {
			if (!before.equals(byId.get(before.id()))) {
				return false;
			}
			byId.put(after.id(), after);
			return true;
		}
	}

	static final class Endpoints implements WebhookEndpoints {

		final Map<Long, WebhookEndpoint> byProvider = new HashMap<>();

		@Override
		public Optional<WebhookEndpoint> find(long providerId) {
			return Optional.ofNullable(byProvider.get(providerId));
		}

		@Override
		public void save(WebhookEndpoint endpoint) {
			byProvider.put(endpoint.providerId(), endpoint);
		}
	}

	/** The identities: users by email, clients by id, each client's secret. */
	static final class Identities implements ProviderIdentities {

		final Map<String, String> passwordByEmail = new HashMap<>();
		final Map<String, String> subjectByEmail = new HashMap<>();
		final Map<String, String> secretByClient = new LinkedHashMap<>();
		private int clients;

		@Override
		public String createPortalUser(String email, String password) {
			if (password.length() < 8) {
				throw new InvalidField("password", "Too short.");
			}
			passwordByEmail.put(email, password);
			return subjectByEmail.computeIfAbsent(email, any -> "sub-" + email);
		}

		@Override
		public ClientCredentials createClient(long providerId, String organisationName) {
			clients++;
			ClientCredentials client = new ClientCredentials("client-" + providerId + "-" + clients,
					"secret-" + providerId + "-" + clients + "-wxyz");
			secretByClient.put(client.clientId(), client.clientSecret());
			return client;
		}

		@Override
		public ClientCredentials replaceClient(String oldClientId, long providerId, String organisationName) {
			secretByClient.remove(oldClientId);
			return createClient(providerId, organisationName);
		}

		@Override
		public String tokenUrl() {
			return "https://auth.test/oauth2/token";
		}
	}

	/** Seals a secret as {@code sealed:<secret>}: plainly not the secret itself, and easy to check. */
	static final class Secrets implements SigningSecrets {

		@Override
		public String store(long providerId, String secret) {
			return "sealed:" + secret;
		}

		@Override
		public String reveal(String sealed) {
			if (!sealed.startsWith("sealed:")) {
				throw new IllegalStateException("Not sealed: " + sealed);
			}
			return sealed.substring("sealed:".length());
		}
	}

	/** The events published, in order, as the outbox would keep them. */
	static final class RecordedEvents implements Events {

		final List<Map.Entry<String, Object>> published = new ArrayList<>();

		@Override
		public void publish(String type, Object payload) {
			published.add(Map.entry(type, payload));
		}
	}
}
