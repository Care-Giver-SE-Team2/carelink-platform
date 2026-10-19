package sg.nus.carelink.report.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.report.domain.model.ProviderJob;

/** The jobs offered to providers. The {@code ForUpdate} reads lock the job until the transaction ends. */
public interface ProviderJobs {

    /** Saves a new job and answers it with its id. */
    ProviderJob create(ProviderJob job);

    /** Saves a change to a job read {@code ForUpdate} in the same transaction. */
    void update(ProviderJob job);

    Optional<ProviderJob> find(Long jobId);

    Optional<ProviderJob> findForUpdate(Long jobId);

    Optional<ProviderJob> findByRequestId(Long requestId);

    Optional<ProviderJob> findByRequestIdForUpdate(Long requestId);

    /** The provider's jobs in these states (all when empty), newest dispatch first. */
    List<ProviderJob> findByProvider(Long providerId, Set<ProviderJob.Status> statuses, int page, int size);

    long countByProvider(Long providerId, Set<ProviderJob.Status> statuses);

    /** How many of the provider's jobs are in each state; a state with none is left out. */
    Map<ProviderJob.Status, Long> countsByStatus(Long providerId);

    /** The ids of NEW jobs whose answer was due before {@code now}. */
    List<Long> findUnansweredBefore(LocalDateTime now);

    /** The action a key was first used for, if it was. */
    Optional<KeyUse> findKey(Long providerId, String key);

    /** Remembers a key that succeeded. */
    void keepKey(Long providerId, String key, KeyUse use, LocalDateTime at);

    /** Forgets keys used before {@code cutoff}; answers how many. */
    int forgetKeysBefore(LocalDateTime cutoff);

    /** What an {@code Idempotency-Key} was used for. */
    record KeyUse(Long jobId, Action action) {
    }

    enum Action {
        ACCEPT, DECLINE, COMPLETE
    }
}
