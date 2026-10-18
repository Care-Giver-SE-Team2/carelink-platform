package sg.nus.carelink.rostering.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.*;
import sg.nus.carelink.rostering.domain.repository.*;
import sg.nus.carelink.rostering.domain.service.WorkSettingsRules;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;

@Service
@Transactional(isolation=Isolation.REPEATABLE_READ,readOnly=true)
public class CaregiverWorkSettingsService implements CaregiverWorkSettingsReader {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final CaregiverWorkDirectory directory;
    private final CaregiverWorkSettingsStore store;
    private final RosteringProfiles profiles;
    private final RosteringConstraintRepository constraints;
    private final AbsenceReportRepository absences;
    private final VisitReassignment visits;
    private final Clock clock;
    private final List<String> serviceCodes;
    public CaregiverWorkSettingsService(CaregiverWorkDirectory directory,CaregiverWorkSettingsStore store,RosteringProfiles profiles,
            RosteringConstraintRepository constraints,AbsenceReportRepository absences,VisitReassignment visits,Clock clock,
            @Value("${carelink.caregiver.work-settings.service-codes:}") String codes) {
        this.directory=directory; this.store=store; this.profiles=profiles; this.constraints=constraints;
        this.absences=absences; this.visits=visits; this.clock=clock;
        this.serviceCodes=Arrays.stream(codes.split(",")).map(String::strip).filter(s->!s.isEmpty()).distinct().sorted().toList();
        if(serviceCodes.stream().anyMatch(s->!s.matches("[A-Z0-9_-]{1,50}"))) throw new IllegalArgumentException("Work settings service codes must be stable uppercase codes.");
    }
    public View own(String username,LocalDate from,LocalDate to) {
        var actor=directory.require(username); var today=today();
        if(from==null && to==null) { from=today; to=today.plusDays(30); }
        WorkSettingsRules.range(from,to);
        var snapshot=read(Set.of(actor.id()),from,to).get(actor.id());
        final LocalDate first=from,last=to;
        var leave=absences.findByCaregiverId(actor.id()).stream().filter(a->!a.startDate().isAfter(last) && !a.endDate().isBefore(first))
                .map(a->new Leave(a.id(),a.startDate(),a.endDate(),a.status().name())).toList();
        // Existing public projection only; do not expose elder IDs or service notes.
        var bookings=visits.bookingsBetween(from.minusDays(1).atStartOfDay(),to.plusDays(1).atStartOfDay()).stream()
                .filter(b->actor.id().equals(b.caregiverId()) && b.end().isAfter(first.atStartOfDay()))
                .map(b->new Booking(b.visitId(),b.start(),b.end())).toList();
        return new View(snapshot,catalog(),today,today.plusDays(90),bookings,leave,allowed(actor.status()));
    }
    @Override public Map<Long,Snapshot> read(Set<Long> ids,LocalDate from,LocalDate to) {
        WorkSettingsRules.range(from,to);
        if(ids==null || ids.size()>500 || ids.stream().anyMatch(id->id==null || id<1)) throw rule("WORK_SETTINGS_BATCH_INVALID","Read at most 500 caregiver IDs.");
        if(ids.isEmpty()) return Map.of();
        var states=store.states(ids); var preferences=store.preferences(ids); var windows=store.availability(ids,from,to); var catalog=catalog();
        var byCaregiver=windows.stream().collect(Collectors.groupingBy(w->w.caregiverId()));
        var result=new LinkedHashMap<Long,Snapshot>();
        states.forEach((id,state)->{
            var issues=new ArrayList<String>(); var preference=WorkSettingsRules.read(preferences.get(id),catalog,issues);
            var byDay=byCaregiver.getOrDefault(id,List.of()).stream().collect(Collectors.groupingBy(w->w.availableDate(),TreeMap::new,Collectors.toList()));
            var days=new ArrayList<Day>();
            byDay.forEach((date,rows)->{
                var list=rows.stream().map(w->new Window(w.availableStart(),w.availableEnd(),w.status())).toList();
                try { WorkSettingsRules.windows(list); } catch(BusinessRuleViolation _) { issues.add("LEGACY_AVAILABILITY_INVALID:"+date); }
                days.add(new Day(date,list));
            });
            result.put(id,new Snapshot(id,state.version(),state.updatedAt(),from,to,preference,List.copyOf(days),issues.stream().distinct().toList(),
                    "Asia/Singapore","UNSPECIFIED","NOT_APPLIED_TO_ROSTERING"));
        });
        return Collections.unmodifiableMap(result);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Snapshot replaceDay(String username,LocalDate date,List<Window> input,long version,String key) {
        // Replay validation occurs before date/catalog policy, so an accepted request
        // can be retried tomorrow without mutating yesterday's declarations.
        return command(username,"AVAILABILITY",List.of(date,input),version,key,date,date,()->{
            WorkSettingsRules.editableDate(date,today());
            store.replaceDay(directory.require(username).id(),date,WorkSettingsRules.windows(input));
        });
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Snapshot preferences(String username,Preferences input,long version,String key) {
        return command(username,"PREFERENCES",input,version,key,today(),today().plusDays(30),()->
                store.preferences(directory.require(username).id(),WorkSettingsRules.preferences(input,catalog()),now()));
    }
    private Snapshot command(String username,String action,Object payload,long version,String key,LocalDate from,LocalDate to,Runnable change) {
        try { if(!UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); }
        catch(RuntimeException _) { throw rule("WORK_SETTINGS_REQUEST_INVALID","Use a canonical UUID request key."); }
        var actor=directory.require(username);
        if(!allowed(store.lock(actor.id()))) throw new AccessDeniedException("This profile cannot update work settings.");
        // Recheck the enabled account after acquiring the profile lock.
        directory.require(username);
        String hash=hash(JSON.writeValueAsString(Arrays.asList(action,actor.id(),version,payload)));
        var receipt=store.receipt(actor.userId(),key);
        if(receipt.isPresent()) {
            if(!receipt.get().hash().equals(hash)) throw rule("WORK_SETTINGS_REQUEST_CONFLICT","This request key was used for different settings.");
            return receipt.get().snapshot();
        }
        if(store.states(Set.of(actor.id())).get(actor.id()).version()!=version) throw rule("WORK_SETTINGS_VERSION_CONFLICT","Settings changed. Refresh and review before saving again.");
        change.run(); store.advance(actor.id(),now()); store.audit(actor.userId(),actor.id(),action,now());
        var saved=read(Set.of(actor.id()),from,to).get(actor.id()); store.receipt(actor.userId(),key,hash,saved); return saved;
    }
    private Catalog catalog() {
        var sectors=profiles.candidates().stream().map(RosteringProfiles.Candidate::sector).filter(Objects::nonNull)
                .filter(s->!s.isBlank() && !s.contains(",") && s.length()<=50).distinct().sorted().toList();
        int visitCap=8; BigDecimal hoursCap=new BigDecimal("8.0");
        for(var c:constraints.findAll()) if(c.enabled() && c.parameterValue()!=null) {
            try {
                if(c.code().equals("DAILY_VISIT_CAP")) visitCap=Math.min(visitCap,Integer.parseInt(c.parameterValue()));
                if(c.code().equals("DAILY_HOURS_CAP")) hoursCap=hoursCap.min(new BigDecimal(c.parameterValue()));
            } catch(NumberFormatException _) { throw rule("WORK_SETTINGS_CONFIGURATION_INVALID","The institution's workload limit needs manager review."); }
        }
        if(visitCap<1 || hoursCap.compareTo(new BigDecimal("0.5"))<0) throw rule("WORK_SETTINGS_CONFIGURATION_INVALID","The institution's workload limit needs manager review.");
        return new Catalog(serviceCodes,sectors,visitCap,hoursCap,!serviceCodes.isEmpty());
    }
    private static boolean allowed(String status) { return status!=null && Set.of("ONBOARDING","AVAILABLE","BUSY").contains(status); }
    private LocalDate today() { return now().toLocalDate(); }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore"))).withNano(0); }
    private static BusinessRuleViolation rule(String code,String message) { return new BusinessRuleViolation(code,message); }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
