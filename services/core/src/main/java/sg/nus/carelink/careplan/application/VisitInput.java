package sg.nus.carelink.careplan.application;

import java.time.LocalTime;

/** One scheduled day for a task, e.g. Mon starting 08:00 for 30 minutes. */
public record VisitInput(String day, LocalTime startTime, int minutes) {
}
