package sg.nus.carelink.report.controller.dto;

import sg.nus.carelink.report.domain.model.Provider;

/** A provider a manager may offer an extra service to. */
public record ProviderOptionResponse(Long providerId, String organisationName) {

    public static ProviderOptionResponse from(Provider provider) {
        return new ProviderOptionResponse(provider.id(), provider.organisationName());
    }
}
