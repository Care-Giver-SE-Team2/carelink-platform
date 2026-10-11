package sg.nus.carelink.visit.domain.repository;

import java.util.Optional;
import sg.nus.carelink.visit.domain.model.Visit;

/** Commands serialize on the parent visit, including commands that only change a task. */
public interface VisitCommandRepository {
    Optional<Visit> lock(Long id);
    Visit save(Visit visit);
}
