package sg.nus.carelink.rostering.domain.repository;

import java.time.*;
import java.util.*;
import sg.nus.carelink.rostering.domain.model.CaregiverAvailability;
import sg.nus.carelink.rostering.domain.model.CaregiverPreference;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.*;

public interface CaregiverWorkSettingsStore {
    String lock(Long caregiverId);
    Map<Long,State> states(Set<Long> caregiverIds);
    Map<Long,CaregiverPreference> preferences(Set<Long> caregiverIds);
    List<CaregiverAvailability> availability(Set<Long> caregiverIds,LocalDate from,LocalDate to);
    void replaceDay(Long caregiverId,LocalDate date,List<Window> windows);
    void preferences(Long caregiverId,Preferences preferences,LocalDateTime now);
    void advance(Long caregiverId,LocalDateTime now);
    Optional<Receipt> receipt(Long actorId,String key);
    void receipt(Long actorId,String key,String hash,Snapshot result);
    void audit(Long actorId,Long caregiverId,String action,LocalDateTime now);
}
