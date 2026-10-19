package sg.nus.carelink.report.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * An approved Open Platform provider, as report keeps it from notification's {@code ProviderUpdated}:
 * whom a job may be offered to, and whose token is whose. notification owns the provider; this is a
 * read copy (docs/section5/decisions.md, D3).
 *
 * @param clientId the OAuth2 client the job API's token names; changes when the provider rotates its secret
 * @param services names from the value-added catalogue
 */
public record Provider(Long id, String clientId, String organisationName, Status status, List<String> services,
        int version) {

    public Provider {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        services = services == null ? List.of() : List.copyOf(services);
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }

    public enum Status {
        ACTIVE, SUSPENDED
    }
}
