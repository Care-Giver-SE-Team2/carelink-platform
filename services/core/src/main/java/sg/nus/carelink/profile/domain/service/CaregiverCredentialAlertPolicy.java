package sg.nus.carelink.profile.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import sg.nus.carelink.profile.domain.model.Credential;

/** Read-only caregiver reminders, not credential approval or rostering eligibility. */
public final class CaregiverCredentialAlertPolicy {
    public enum RenewalState { NONE, PENDING_REVIEW, REJECTED, APPROVED_NOT_EFFECTIVE, REVOKED, CHECK_REQUIRED }
    public record Alert(Credential credential, String warning, long daysUntilExpiry,
                        RenewalState renewalState, LocalDate renewalValidFrom) {}
    public record Evaluation(List<Alert> alerts, boolean reviewRequired) {}

    public Evaluation evaluate(Long caregiverId, List<Credential> credentials, LocalDate today, int warningDays) {
        if (warningDays < 0) throw new IllegalArgumentException("Warning days must be non-negative");
        var graph = new RenewalGraph(caregiverId, credentials, today);
        var alerts = new ArrayList<Alert>();
        for (var credential : graph.rows.values()) {
            boolean needsReview = graph.review.contains(credential.id());
            if (!isDue(credential, today, warningDays, needsReview)) continue;
            var successors = graph.children.getOrDefault(credential.id(), List.of());
            Credential successor = successors.size() == 1 ? successors.getFirst() : null;
            if (!needsReview && successor != null && approved(successor) && effective(successor, today)) continue;
            var renewal = renewalState(successor, needsReview);
            alerts.add(new Alert(credential, credential.expiryDate().isBefore(today) ? "EXPIRED" : "EXPIRING",
                    ChronoUnit.DAYS.between(today, credential.expiryDate()), renewal,
                    renewal == RenewalState.APPROVED_NOT_EFFECTIVE ? successor.validFrom() : null));
        }
        alerts.sort(Comparator.comparing((Alert a) -> a.credential().expiryDate()).thenComparing(a -> a.credential().id()));
        return new Evaluation(List.copyOf(alerts), !graph.review.isEmpty());
    }

    private static boolean isDue(Credential credential, LocalDate today, int warningDays, boolean needsReview) {
        return approved(credential) && credential.expiryDate() != null
                && !credential.expiryDate().equals(LocalDate.of(9999, 12, 31))
                && ChronoUnit.DAYS.between(today, credential.expiryDate()) <= warningDays
                && (effective(credential, today) || needsReview);
    }

    private static boolean approved(Credential credential) {
        return credential.status() == Credential.Status.PUBLISHED || credential.status() == Credential.Status.EXPIRING
                || credential.status() == Credential.Status.EXPIRED;
    }

    private static boolean effective(Credential credential, LocalDate today) {
        // Existing data uses null for an unspecified start date; publication has no future-start restriction.
        return credential.validFrom() == null || !credential.validFrom().isAfter(today);
    }

    private static RenewalState renewalState(Credential successor, boolean needsReview) {
        if (needsReview) return RenewalState.CHECK_REQUIRED;
        if (successor == null) return RenewalState.NONE;
        return switch (successor.status()) {
            case SUBMITTED -> RenewalState.PENDING_REVIEW;
            case REJECTED -> RenewalState.REJECTED;
            case REVOKED -> RenewalState.REVOKED;
            case PUBLISHED, EXPIRING, EXPIRED -> RenewalState.APPROVED_NOT_EFFECTIVE;
        };
    }

    private static boolean inconsistentDates(Credential c, LocalDate today) {
        if (c.status() == null || c.expiryDate() == null) return true;
        return (c.validFrom() != null && c.validFrom().isAfter(c.expiryDate()))
                || (c.status() == Credential.Status.EXPIRED && !c.expiryDate().isBefore(today));
    }

    /** Invalid connected components never suppress an ancestor; no cross-caregiver lookups are made. */
    private static final class RenewalGraph {
        private final Map<Long, Credential> rows = new HashMap<>();
        private final Map<Long, List<Credential>> children = new HashMap<>();
        private final Set<Long> review = new HashSet<>();

        private RenewalGraph(Long caregiverId, List<Credential> credentials, LocalDate today) {
            credentials.stream().filter(c -> Objects.equals(caregiverId, c.caregiverId()))
                    .forEach(c -> rows.put(c.id(), c));
            for (var c : rows.values()) {
                if (inconsistentDates(c, today)) review.add(c.id());
                connect(c, today);
            }
            children.forEach((id, successors) -> { if (successors.size() > 1) review.add(id); });
            detectCycles();
            propagateReview();
        }

        private void connect(Credential c, LocalDate today) {
            if (c.renewsCredentialId() == null) return;
            var parent = rows.get(c.renewsCredentialId());
            if (parent == null) {
                review.add(c.id());
                return;
            }
            children.computeIfAbsent(parent.id(), ignored -> new ArrayList<>()).add(c);
            boolean typeMismatch = !Objects.equals(c.credentialTypeId(), parent.credentialTypeId());
            boolean invalidApproval = approved(c) && (!approved(parent)
                    || (effective(c, today) && !effective(parent, today)));
            if (typeMismatch || invalidApproval) review.add(c.id());
        }

        private void detectCycles() {
            var complete = new HashSet<Long>();
            for (var id : rows.keySet()) {
                var path = new HashSet<Long>();
                Long cursor = id;
                while (cursor != null && rows.containsKey(cursor) && !complete.contains(cursor)) {
                    if (!path.add(cursor)) {
                        review.add(cursor);
                        break;
                    }
                    cursor = rows.get(cursor).renewsCredentialId();
                }
                complete.addAll(path);
            }
        }

        private void propagateReview() {
            var queue = new ArrayDeque<>(review);
            while (!queue.isEmpty()) {
                Long id = queue.removeFirst();
                Long parent = rows.get(id).renewsCredentialId();
                if (parent != null && rows.containsKey(parent) && review.add(parent)) queue.addLast(parent);
                for (var child : children.getOrDefault(id, List.of())) {
                    if (review.add(child.id())) queue.addLast(child.id());
                }
            }
        }
    }
}
