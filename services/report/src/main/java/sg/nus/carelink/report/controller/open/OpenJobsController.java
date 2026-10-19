package sg.nus.carelink.report.controller.open;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.platform.security.OpenPlatformCallers;
import sg.nus.carelink.report.application.ProviderJobService;
import sg.nus.carelink.report.domain.model.ProviderJob;

/**
 * The Open Platform's job API (docs/api/open-platform.yaml, Jobs), for a provider's server: its token
 * was checked, and its scope, by platform-security's {@code /api/open/**} chain before this runs.
 * Errors are {@link OpenJobsProblems}'.
 */
@RestController
@Validated
@RequestMapping("/api/open/v1/jobs")
public class OpenJobsController {

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final ProviderJobService jobs;
	private final OpenPlatformCallers callers;

	public OpenJobsController(ProviderJobService jobs, OpenPlatformCallers callers) {
		this.jobs = jobs;
		this.callers = callers;
	}

	@GetMapping
	public OpenJobDtos.JobPage list(@RequestParam(required = false) List<ProviderJob.Status> status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		Set<ProviderJob.Status> statuses = status == null ? Set.of() : Set.copyOf(status);
		return OpenJobDtos.JobPage.of(jobs.list(client(), statuses, page, size));
	}

	@GetMapping("/{jobId}")
	public OpenJobDtos.Job get(@PathVariable String jobId) {
		return OpenJobDtos.Job.of(jobs.get(client(), OpenJobDtos.jobId(jobId)));
	}

	@PostMapping("/{jobId}/accept")
	public OpenJobDtos.Job accept(@PathVariable String jobId,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key) {
		return OpenJobDtos.Job.of(jobs.accept(client(), OpenJobDtos.jobId(jobId), OpenJobDtos.key(key)));
	}

	@PostMapping("/{jobId}/decline")
	public OpenJobDtos.Job decline(@PathVariable String jobId,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
			@Valid @RequestBody OpenJobDtos.JobDecline body) {
		return OpenJobDtos.Job.of(jobs.decline(client(), OpenJobDtos.jobId(jobId), OpenJobDtos.key(key),
				body.reason(), body.note()));
	}

	@PostMapping("/{jobId}/complete")
	public OpenJobDtos.Job complete(@PathVariable String jobId,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
			@Valid @RequestBody OpenJobDtos.JobCompletion body) {
		return OpenJobDtos.Job.of(jobs.complete(client(), OpenJobDtos.jobId(jobId), OpenJobDtos.key(key),
				SingaporeTime.local(body.actualStart()), SingaporeTime.local(body.actualEnd()), body.note()));
	}

	private String client() {
		return callers.requireClient().clientId();
	}
}
