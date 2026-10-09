package sg.nus.carelink.careplan.application;

import java.util.List;

/** Reads the precise version held by a visit, never the elder's latest plan. */
public interface VisitPlanReader {
    Snapshot read(Long planId, Long elderId);
    record Task(Long id, String name, String evidenceType) {}
    record Snapshot(Long id, Integer version, List<Task> tasks) {}
}
