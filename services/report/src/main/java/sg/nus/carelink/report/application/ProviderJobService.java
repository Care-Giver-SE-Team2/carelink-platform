package sg.nus.carelink.report.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.report.domain.model.Provider;
import sg.nus.carelink.report.domain.model.ProviderJob;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.repository.ProviderJobs;
import sg.nus.carelink.report.domain.repository.ProviderJobs.Action;
import sg.nus.carelink.report.domain.repository.ProviderJobs.KeyUse;
import sg.nus.carelink.report.domain.repository.Providers;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The Open Platform's job API (docs/api/open-platform.yaml, Jobs): a provider reads the jobs it was
 * offered and accepts, declines or completes them. The caller is the provider whose token names the
 * client; a suspended provider is refused.
 *
 * <p>The request also has a visit nobody holds, which a manager may staff while the job is open (D17).
 * Accepting, a manager staffing the visit and the family cancelling are each decided on the locked job,
 * in report (D1): whichever comes second sees the other. Accepting calls the visit off; a visit staffed
 * some other way withdraws the job instead. A declined job, or one whose time to answer ran out, just
 * closes: the manager already has the visit. Completing the job completes the request.
 *
 * <p>Each answer is kept under its {@code Idempotency-Key} for {@link #KEYS_KEPT}: the same key again
 * answers the job as it stands, and acts no more.
 */
@Service
@Transactional
public class ProviderJobService {

    static final Duration KEYS_KEPT = Duration.ofHours(24);

    static final String TAKEN_BY_PROVIDER = "A provider has taken this service on";

    private final Providers providers;
    private final ProviderJobs jobs;
    private final ProviderJobEvents events;
    private final JobElders elders;
    private final ValueAddedServiceRequestRepository requests;
    private final StandaloneVisits visits;
    private final VisitReassignment reassignment;
    private final Clock clock;

    @SuppressWarnings("java:S107") // one collaborator per thing a job reaches
    public ProviderJobService(Providers providers, ProviderJobs jobs, ProviderJobEvents events, JobElders elders,
            ValueAddedServiceRequestRepository requests, StandaloneVisits visits, VisitReassignment reassignment,
            Clock clock) {
        this.providers = providers;
        this.jobs = jobs;
        this.events = events;
        this.elders = elders;
        this.requests = requests;
        this.visits = visits;
        this.reassignment = reassignment;
        this.clock = clock;
    }

    /** The provider's jobs, newest dispatch first, with every state's count. */
    @Transactional(readOnly = true)
    public JobPage list(String clientId, Set<ProviderJob.Status> statuses, int page, int size) {
        Provider provider = caller(clientId);
        return new JobPage(jobs.findByProvider(provider.id(), statuses, page, size), page, size,
                jobs.countByProvider(provider.id(), statuses), jobs.countsByStatus(provider.id()));
    }

    /** One of the provider's jobs; the address only once it has accepted it. */
    @Transactional(readOnly = true)
    public JobView get(String clientId, Long jobId) {
        Provider provider = caller(clientId);
        return view(jobs.find(jobId).filter(job -> job.providerId().equals(provider.id()))
                .orElseThrow(JobNotFound::new));
    }

    /**
     * The provider takes the job on, and the request's visit is called off: the provider was first.
     * If its time to answer has run out, or somebody was put on the visit in the meantime, the job is
     * called off here and now, and kept so, though the provider is refused.
     */
    @Transactional(noRollbackFor = BusinessRuleViolation.class)
    public JobView accept(String clientId, Long jobId, String key) {
        Provider provider = caller(clientId);
        Optional<JobView> replayed = replay(provider, jobId, key, Action.ACCEPT);
        if (replayed.isPresent()) {
            return replayed.get();
        }
        LocalDateTime now = now();
        ProviderJob job = expireIfDue(lockOwned(provider, jobId), now);
        Long visitId = requests.findById(job.requestId()).map(ValueAddedServiceRequest::visitId).orElse(null);
        if (job.status() == ProviderJob.Status.NEW) {
            withdrawIfStaffed(job, visitId, now);
        }
        ProviderJob accepted = job.accepted(now);
        save(accepted);
        if (visitId != null) {
            // A failure here refuses the acceptance and rolls it back; the provider can send it again
            reassignment.callOff(visitId, new VisitReassignment.Change(null, null, null, TAKEN_BY_PROVIDER));
        }
        jobs.keepKey(provider.id(), key, new KeyUse(jobId, Action.ACCEPT), now);
        return view(accepted);
    }

    /** The provider turns the job down; the manager already has the visit. */
    @Transactional(noRollbackFor = BusinessRuleViolation.class)
    public JobView decline(String clientId, Long jobId, String key, ProviderJob.DeclineReason reason, String note) {
        Provider provider = caller(clientId);
        Optional<JobView> replayed = replay(provider, jobId, key, Action.DECLINE);
        if (replayed.isPresent()) {
            return replayed.get();
        }
        LocalDateTime now = now();
        ProviderJob job = expireIfDue(lockOwned(provider, jobId), now);
        ProviderJob declined = job.declined(new ProviderJob.Decline(reason, note, now));
        save(declined);
        jobs.keepKey(provider.id(), key, new KeyUse(jobId, Action.DECLINE), now);
        return view(declined);
    }

    /** The provider reports the work done, which completes the request. */
    public JobView complete(String clientId, Long jobId, String key, LocalDateTime actualStart,
            LocalDateTime actualEnd, String note) {
        Provider provider = caller(clientId);
        Optional<JobView> replayed = replay(provider, jobId, key, Action.COMPLETE);
        if (replayed.isPresent()) {
            return replayed.get();
        }
        LocalDateTime now = now();
        ProviderJob completed = lockOwned(provider, jobId)
                .completed(new ProviderJob.Completion(actualStart, actualEnd, note, now));
        save(completed);
        requests.findById(completed.requestId())
                .filter(request -> request.status() == ValueAddedServiceRequest.Status.DISPATCHED)
                .ifPresent(request -> requests.save(request.completed()));
        jobs.keepKey(provider.id(), key, new KeyUse(jobId, Action.COMPLETE), now);
        return view(completed);
    }

    /** The NEW jobs whose time to answer has run out, for {@link #expire} one at a time. */
    @Transactional(readOnly = true)
    public List<Long> unanswered() {
        return jobs.findUnansweredBefore(now());
    }

    /**
     * Calls off a NEW job whose time to answer has run out; the manager already has the visit.
     *
     * @return whether it was; false when it was answered in the meantime
     */
    public boolean expire(Long jobId) {
        LocalDateTime now = now();
        return jobs.findForUpdate(jobId)
                .filter(job -> job.status() == ProviderJob.Status.NEW && now.isAfter(job.respondBy()))
                .map(job -> {
                    save(job.expired(now));
                    return true;
                })
                .orElse(false);
    }

    /** Forgets the Idempotency-Keys older than {@link #KEYS_KEPT}. */
    public int forgetOldKeys() {
        return jobs.forgetKeysBefore(now().minus(KEYS_KEPT));
    }

    private ProviderJob expireIfDue(ProviderJob job, LocalDateTime now) {
        if (job.status() == ProviderJob.Status.NEW && now.isAfter(job.respondBy())) {
            save(job.expired(now));
            throw new BusinessRuleViolation("RESPONSE_DEADLINE_PASSED",
                    "The time to answer this job has passed; CareLink has made other arrangements.");
        }
        return job;
    }

    /**
     * Somebody was put on the visit, or it is no longer to happen, without going through the manager's
     * screen (which withdraws the job itself): the job is withdrawn and the provider refused.
     */
    private void withdrawIfStaffed(ProviderJob job, Long visitId, LocalDateTime now) {
        if (visitId == null) {
            return;
        }
        Optional<StandaloneVisits.State> visit = visits.find(visitId);
        if (visit.isPresent() && (visit.get().caregiverId() != null || !"SCHEDULED".equals(visit.get().status()))) {
            save(job.cancelled(ProviderJobDispatch.STAFFED_REASON, now));
            throw new BusinessRuleViolation("JOB_CANCELLED", ProviderJobDispatch.STAFFED_REASON);
        }
    }

    private void save(ProviderJob job) {
        jobs.update(job);
        events.changed(job);
    }

    private Optional<JobView> replay(Provider provider, Long jobId, String key, Action action) {
        return jobs.findKey(provider.id(), key).map(use -> {
            if (!use.jobId().equals(jobId) || use.action() != action) {
                throw new KeyReused();
            }
            return view(jobs.find(jobId).orElseThrow(JobNotFound::new));
        });
    }

    private ProviderJob lockOwned(Provider provider, Long jobId) {
        return jobs.findForUpdate(jobId).filter(job -> job.providerId().equals(provider.id()))
                .orElseThrow(JobNotFound::new);
    }

    private Provider caller(String clientId) {
        Provider provider = providers.findByClientId(clientId).orElseThrow(UnknownClient::new);
        if (!provider.active()) {
            throw new ProviderSuspended();
        }
        return provider;
    }

    private JobView view(ProviderJob job) {
        Optional<ValueAddedServiceRequest> request = requests.findById(job.requestId());
        Long elderId = request.map(ValueAddedServiceRequest::elderId).orElse(null);
        boolean accepted = job.status() == ProviderJob.Status.ACCEPTED || job.status() == ProviderJob.Status.COMPLETED;
        return new JobView(job, request.map(ValueAddedServiceRequest::specialInstructions).orElse(null),
                accepted && elderId != null ? elders.address(elderId) : null);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    /** A job as the provider reads it; {@code address} is null until it has accepted. */
    public record JobView(ProviderJob job, String instructions, JobElders.Address address) {
    }

    /** {@code counts} leaves out a state with no jobs. */
    public record JobPage(List<ProviderJob> items, int page, int size, long totalElements,
            Map<ProviderJob.Status, Long> counts) {
    }

    /** The token's client is no provider's now: its secret was rotated, or report has not heard of it yet. */
    public static final class UnknownClient extends RuntimeException {
        public UnknownClient() {
            super("The token's client is not a provider's.");
        }
    }

    public static final class ProviderSuspended extends RuntimeException {
        public ProviderSuspended() {
            super("CareLink has suspended this provider.");
        }
    }

    public static final class JobNotFound extends RuntimeException {
        public JobNotFound() {
            super("No job with that ID was dispatched to this provider.");
        }
    }

    public static final class KeyReused extends RuntimeException {
        public KeyReused() {
            super("This Idempotency-Key was used for another job or another action.");
        }
    }
}
