package sg.nus.carelink.report.controller.dto;

import jakarta.validation.constraints.NotNull;

/** The provider a manager offers an extra service to. */
public record ProviderOfferRequest(@NotNull Long providerId) {
}
