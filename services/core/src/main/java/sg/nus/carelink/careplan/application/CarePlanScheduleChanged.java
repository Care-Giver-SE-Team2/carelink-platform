package sg.nus.carelink.careplan.application;

/**
 * Published after a care plan is published or stopped, so the elder's visits can follow the
 * new schedule. An event rather than a call because rostering already depends on careplan;
 * careplan calling rostering back would make the two modules a cycle, which
 * LayerDependencyTest rejects.
 */
public record CarePlanScheduleChanged(Long elderId) {
}
