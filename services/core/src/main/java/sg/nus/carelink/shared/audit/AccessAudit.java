package sg.nus.carelink.shared.audit;

/** Append-only access evidence; contains resource identifiers, never care details. */
public interface AccessAudit {
    void workPack(Long userId, Long visitId, String result);
}
