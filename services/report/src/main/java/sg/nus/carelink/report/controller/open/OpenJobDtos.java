package sg.nus.carelink.report.controller.open;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.report.application.JobElders;
import sg.nus.carelink.report.application.ProviderJobService;
import sg.nus.carelink.report.domain.model.ProviderJob;

/** The job API's bodies, as docs/api/open-platform.yaml names them. Times go out on Singapore's offset. */
final class OpenJobDtos {

	private static final String PREFIX = "JOB-";

	private OpenJobDtos() {
	}

	/** {@code JOB-24081} to 24081; anything else is no job of the provider's. */
	static Long jobId(String shown) {
		if (shown == null || !shown.startsWith(PREFIX)) {
			throw new ProviderJobService.JobNotFound();
		}
		try {
			return Long.valueOf(shown.substring(PREFIX.length()));
		}
		catch (NumberFormatException notANumber) {
			throw new ProviderJobService.JobNotFound();
		}
	}

	/** The {@code Idempotency-Key}: required, 1 to 120 characters. */
	static String key(String key) {
		if (key == null || key.isEmpty()) {
			throw new KeyMissing();
		}
		if (key.length() > 120) {
			throw new KeyTooLong();
		}
		return key;
	}

	static String shown(Long jobId) {
		return PREFIX + jobId;
	}

	record JobSummary(String id, int version, ProviderJob.Status status, String serviceName, String district,
			String areaName, OffsetDateTime scheduledStart, int durationMinutes, OffsetDateTime respondBy,
			OffsetDateTime receivedAt, String cancelReason) {

		static JobSummary of(ProviderJob job) {
			return new JobSummary(shown(job.id()), job.version(), job.status(), job.serviceName(), job.district(),
					job.areaName(), SingaporeTime.of(job.scheduledStart()), job.durationMinutes(),
					SingaporeTime.of(job.respondByShown()), SingaporeTime.of(job.dispatchedAt()), job.cancelReason());
		}
	}

	record Job(String id, int version, ProviderJob.Status status, String serviceName, String district,
			String areaName, OffsetDateTime scheduledStart, int durationMinutes, OffsetDateTime respondBy,
			OffsetDateTime receivedAt, String cancelReason, String elderSummary, String languages, String bookedBy,
			String instructions, Address address, List<TimelineStep> timeline, Completion completion,
			Decline decline) {

		static Job of(ProviderJobService.JobView view) {
			ProviderJob job = view.job();
			JobSummary summary = JobSummary.of(job);
			JobElders.Address address = view.address();
			return new Job(summary.id(), summary.version(), summary.status(), summary.serviceName(),
					summary.district(), summary.areaName(), summary.scheduledStart(), summary.durationMinutes(),
					summary.respondBy(), summary.receivedAt(), summary.cancelReason(), job.elderSummary(),
					job.languages(), job.bookedBy(), view.instructions(),
					address == null ? null : new Address(address.text(), address.accessNotes()), timeline(job),
					job.completion() == null ? null : new Completion(SingaporeTime.of(job.completion().actualStart()),
							SingaporeTime.of(job.completion().actualEnd()), job.completion().note()),
					job.decline() == null ? null : new Decline(job.decline().reason(), job.decline().note()));
		}

		private static List<TimelineStep> timeline(ProviderJob job) {
			List<TimelineStep> steps = new ArrayList<>();
			steps.add(new TimelineStep("DISPATCHED", SingaporeTime.of(job.dispatchedAt())));
			add(steps, "ACCEPTED", job.acceptedAt());
			add(steps, "DECLINED", job.decline() == null ? null : job.decline().at());
			add(steps, "COMPLETED", job.completion() == null ? null : job.completion().at());
			add(steps, "CANCELLED", job.cancelledAt());
			return steps;
		}

		private static void add(List<TimelineStep> steps, String step, LocalDateTime at) {
			if (at != null) {
				steps.add(new TimelineStep(step, SingaporeTime.of(at)));
			}
		}
	}

	record Address(String text, String accessNotes) {
	}

	record TimelineStep(String step, OffsetDateTime at) {
	}

	record Completion(OffsetDateTime actualStart, OffsetDateTime actualEnd, String note) {
	}

	record Decline(ProviderJob.DeclineReason reason, String note) {
	}

	record JobPage(List<JobSummary> items, int page, int size, long totalElements, Map<String, Long> counts) {

		static JobPage of(ProviderJobService.JobPage page) {
			Map<String, Long> counts = new LinkedHashMap<>();
			for (ProviderJob.Status status : ProviderJob.Status.values()) {
				counts.put(status.name(), page.counts().getOrDefault(status, 0L));
			}
			return new JobPage(page.items().stream().map(JobSummary::of).toList(), page.page(), page.size(),
					page.totalElements(), counts);
		}
	}

	/** The body of a decline. A note is required for OTHER, which the job itself checks. */
	record JobDecline(@NotNull ProviderJob.DeclineReason reason, @Size(max = 500) String note) {
	}

	record JobCompletion(@NotNull OffsetDateTime actualStart, @NotNull OffsetDateTime actualEnd,
			@Size(max = 2000) String note) {
	}

	/** 400 {@code IDEMPOTENCY_KEY_REQUIRED}. */
	static final class KeyMissing extends RuntimeException {
		KeyMissing() {
			super("The Idempotency-Key header is required.");
		}
	}

	/** 400 {@code VALIDATION_FAILED} on the header. */
	static final class KeyTooLong extends RuntimeException {
		KeyTooLong() {
			super("At most 120 characters.");
		}
	}
}
