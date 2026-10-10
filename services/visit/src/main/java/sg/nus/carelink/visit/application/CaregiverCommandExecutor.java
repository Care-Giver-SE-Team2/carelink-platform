package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.BiFunction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitStateTransition;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;
import sg.nus.carelink.visit.domain.repository.VisitStateTransitionRepository;

/** Owns the transaction boundary so rejected attempts are recorded AFTER releasing visit locks. */
@Service
public class CaregiverCommandExecutor {
    private final TransactionTemplate tx;
    private final CaregiverWorkDirectory directory;
    private final VisitCommandRepository visits;
    private final VisitStateTransitionRepository transitions;
    private final CaregiverCommandStore store;
    private final Clock clock;
    public CaregiverCommandExecutor(PlatformTransactionManager manager, CaregiverWorkDirectory directory, VisitCommandRepository visits,
            VisitStateTransitionRepository transitions, CaregiverCommandStore store, Clock clock) {
        tx = new TransactionTemplate(manager);
        // Under MySQL REPEATABLE_READ, profile lookup can establish a snapshot before
        // a competing command commits; its receipt is current, but its incident was invisible.
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.directory = directory; this.visits = visits;
        this.transitions = transitions; this.store = store; this.clock = clock;
    }
    public <T> T execute(String username, Long id, String action, String target, BiFunction<CaregiverWorkDirectory.Profile, Visit, T> command) {
        var context = new Context();
        try {
            return tx.execute(status -> {
                var profile = directory.require(username);
                context.actor = profile.userId();
                var visit = visits.lock(id).orElseThrow(() -> new ResourceNotFound("Visit", id));
                context.visit = visit;
                if (!Objects.equals(profile.id(), visit.caregiverId())) throw new AccessDeniedException("Not assigned");
                context.authorized = true;
                return command.apply(profile, visit);
            });
        } catch (BusinessRuleViolation failure) {
            tx.executeWithoutResult(status -> {
                store.audit(context.actor, id, action, "FAILED", failure.code());
                if (context.authorized && target != null && context.visit != null) transitions.save(new VisitStateTransition(null, id,
                        context.visit.status().name(), target, context.actor, VisitStateTransition.Result.REJECTED, failure.code(), now()));
            });
            throw failure;
        } catch (AccessDeniedException | ResourceNotFound failure) {
            tx.executeWithoutResult(status -> store.audit(context.actor, id, action, "DENIED", "ACCESS_DENIED"));
            throw failure;
        } catch (DataIntegrityViolationException _) {
            throw new BusinessRuleViolation("COMMAND_CONFLICT", "Command conflict. Check saved results before trying again.");
        } catch (org.springframework.dao.ConcurrencyFailureException _) {
            throw new BusinessRuleViolation("VISIT_VERSION_CONFLICT", "Concurrent change. Check saved results and refresh before continuing.");
        } catch (org.springframework.dao.DataAccessException failure) {
            throw new CaregiverCommandUnavailable(failure);
        }
    }
    public LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore"))); }
    private static class Context {
        Long actor;
        Visit visit;
        boolean authorized;
    }
    public static void version(Visit visit, Integer expected) {
        if (!Objects.equals(visit.version(), expected)) throw new BusinessRuleViolation("VISIT_VERSION_CONFLICT", "Visit changed. Refresh before continuing.");
    }
}
