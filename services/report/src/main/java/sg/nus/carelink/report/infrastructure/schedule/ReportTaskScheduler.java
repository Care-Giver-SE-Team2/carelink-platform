package sg.nus.carelink.report.infrastructure.schedule;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.application.ReportTaskProducer;

/** Opt-in producer, separate from the legacy synchronous scheduler. */
@Component
@ConditionalOnProperty(name = "carelink.report.task-producer-enabled", havingValue = "true")
class ReportTaskScheduler {
    private final ReportTaskProducer producer;

    ReportTaskScheduler(ReportTaskProducer producer) {
        this.producer = producer;
    }

    @SchedulerLock(name = "report.weekly-task-producer", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    @Scheduled(cron = "${carelink.report.task-producer-cron:0 15 0 * * MON}",
               zone = "Asia/Singapore")
    void publishWeeklyTasks() {
        producer.publishLastCompletedWeek();
    }
}
