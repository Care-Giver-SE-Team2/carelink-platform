package sg.nus.carelink.report.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.report.domain.model.Provider;

/** report's copy of the Open Platform's providers, kept from {@code ProviderUpdated}. */
public interface Providers {

    /** Keeps the provider, unless the copy held is as new or newer, by {@link Provider#version}. */
    void keep(Provider provider);

    Optional<Provider> find(Long providerId);

    /** The provider whose token names this client. */
    Optional<Provider> findByClientId(String clientId);

    /** The active providers offering the service, by name: whom a manager may offer it to. */
    List<Provider> activeFor(String serviceName);
}
