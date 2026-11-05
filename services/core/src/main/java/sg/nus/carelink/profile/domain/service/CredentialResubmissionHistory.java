package sg.nus.carelink.profile.domain.service;

import java.util.*;
import sg.nus.carelink.profile.domain.model.Credential;

/** Only explicit, valid retry lineage can remove a rejected attempt from the alert graph. */
public final class CredentialResubmissionHistory {
    private CredentialResubmissionHistory() { }

    public static List<Credential> forAlerts(List<Credential> rows, Map<Long, Long> retries) {
        Map<Long, Credential> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.id(), row));
        Set<Long> replaced = new HashSet<>();
        for (var entry : retries.entrySet()) {
            Credential next = byId.get(entry.getKey());
            Credential old = byId.get(entry.getValue());
            if (next == null || old == null || old.status() != Credential.Status.REJECTED
                    || !Objects.equals(next.caregiverId(), old.caregiverId())
                    || !Objects.equals(next.credentialTypeId(), old.credentialTypeId())
                    || !Objects.equals(next.renewsCredentialId(), old.renewsCredentialId())) continue;
            Set<Long> seen = new HashSet<>();
            Long cursor = next.id();
            boolean cycle = false;
            while (cursor != null) {
                if (!seen.add(cursor)) { cycle = true; break; }
                cursor = retries.get(cursor);
            }
            if (!cycle) replaced.add(old.id());
        }
        return rows.stream().filter(row -> !replaced.contains(row.id())).toList();
    }
}
