package sg.nus.carelink.rostering.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.Snapshot;

/** Trusted application port, not a public HTTP endpoint. No algorithm consumes it yet.
 * Unknown caregiver IDs are omitted; an existing caregiver with no declarations has
 * empty preferences/windows and UNSPECIFIED uncovered time. Inspect dataIssues before use.
 * Approved leave and institution constraints always take precedence over preferences.
 */
public interface CaregiverWorkSettingsReader {
    Map<Long,Snapshot> read(Set<Long> caregiverIds,LocalDate from,LocalDate to);
}
