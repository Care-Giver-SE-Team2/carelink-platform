package sg.nus.carelink.profile.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CaregiverApplication;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.*;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CaregiverAdmissionStore;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CaregiverAdmissionService implements CaregiverApplicationReader, CaregiverAdmissionCommands {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AccountIssuer accounts;
    private final UserDirectory users;
    private final CaregiverRepository caregivers;
    private final CaregiverAdmissionStore store;
    private final Clock clock;
    public CaregiverAdmissionService(AccountIssuer accounts, UserDirectory users, CaregiverRepository caregivers, CaregiverAdmissionStore store, Clock clock) {
        this.accounts=accounts; this.users=users; this.caregivers=caregivers; this.store=store; this.clock=clock;
    }
    public View register(String username, String password, Details details) {
        Long userId;
        try {
            userId = accounts.register(username,details.fullName(),password,Role.CAREGIVER);
        } catch (org.springframework.dao.DataIntegrityViolationException failure) {
            // The database remains the authority when two registrations race.
            String detail = failure.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains("uk_app_user_username"))
                throw rule("USERNAME_TAKEN", "That username is already taken");
            throw failure;
        }
        var caregiver = caregivers.save(new Caregiver(null,userId,details.fullName(),details.phone(),null,
                details.declaredDialects(),Caregiver.Status.ONBOARDING,null,null));
        store.lockCaregiver(caregiver.id());
        var application = store.save(new CaregiverApplication(null,caregiver.id(),Status.PENDING,1,details,now(),null,null,null));
        store.history(application,userId,"SUBMIT",0,now());
        return view(new AppUser(userId,username,details.fullName(),java.util.Set.of(Role.CAREGIVER),true),caregiver);
    }
    @Transactional(readOnly=true)
    public View own(String username) { var user=actor(username,Role.CAREGIVER); return view(user,profile(user)); }
    public View edit(String username, Details details, long version, String requestId) {
        var user=actor(username,Role.CAREGIVER); var caregiver=profile(user);
        return command(user,caregiver.id(),"EDIT",details,version,requestId,locked -> {
            editable(locked); var a=application(locked.id()).edit(details);
            store.save(a); store.history(a,user.id(),"EDIT",version+1,now()); return a;
        });
    }
    public View resubmit(String username, long version, String requestId) {
        var user=actor(username,Role.CAREGIVER); var caregiver=profile(user);
        return command(user,caregiver.id(),"RESUBMIT",null,version,requestId,locked -> {
            editable(locked); var a=application(locked.id()).resubmit(now());
            store.save(a); store.history(a,user.id(),"RESUBMIT",version+1,now()); return a;
        });
    }
    public View contact(String username, String phone, long version, String requestId) {
        var user=actor(username,Role.CAREGIVER); var caregiver=profile(user);
        return command(user,caregiver.id(),"CONTACT",phone,version,requestId,locked -> {
            if (!locked.isAssignable()) throw denied();
            store.contact(locked.id(),phone);
            store.auditContact(locked.id(),user.id(),now());
            store.findByCaregiver(locked.id()).ifPresent(a -> store.history(a,user.id(),"CONTACT",version+1,now()));
            return null;
        });
    }
    @Override public View decide(String username, Long id, boolean approve, String reason, long version, String requestId) {
        var manager=actor(username,Role.MANAGER);
        var found=store.findById(id).orElseThrow(() -> new ResourceNotFound("Application",id));
        return command(manager,found.caregiverId(),approve ? "APPROVE" : "REJECT",reason,version,requestId,locked -> {
            editable(locked);
            var a=application(locked.id()).decide(approve,manager.id(),reason,now());
            store.save(a);
            if (approve) store.approveProfile(locked.id(),a.details());
            store.history(a,manager.id(),approve ? "APPROVE" : "REJECT",version+1,now());
            return a;
        });
    }
    @Override @Transactional(readOnly=true)
    public Page pendingApplications(String username, int page, int size) {
        actor(username,Role.MANAGER);
        if(page<0 || page>10000 || size<1 || size>100) throw rule("APPLICATION_PAGE_INVALID","Choose a valid page and size (1–100).");
        return new Page(store.pending(page*size,size),page,size,store.pendingCount());
    }
    @Override @Transactional(readOnly=true)
    public View applicationForManager(String username,Long id) {
        actor(username,Role.MANAGER);
        var a=store.findById(id).orElseThrow(() -> new ResourceNotFound("Application",id));
        var c=caregivers.findById(a.caregiverId()).orElseThrow(() -> new ResourceNotFound("Caregiver",a.caregiverId()));
        var user=users.findById(c.userId()).orElseThrow(() -> new ResourceNotFound("Account",c.userId()));
        return view(user,c);
    }
    private View command(AppUser actor,Long caregiverId,String action,Object payload,long version,String key,Function<Caregiver,CaregiverApplication> mutation) {
        canonicalKey(key);
        var locked=store.lockCaregiver(caregiverId);
        if(locked.status()==Caregiver.Status.INACTIVE) throw denied();
        // A disabled applicant cannot be activated by a stale manager request.
        var owner=users.findById(locked.userId()).filter(u -> u.enabled() && u.hasRole(Role.CAREGIVER)).orElseThrow(CaregiverAdmissionService::denied);
        String hash=digest(JSON.writeValueAsString(java.util.Arrays.asList(action,caregiverId,version,payload)));
        var existing=store.receipt(actor.id(),key);
        if(existing.isPresent()) {
            if(!existing.get().payloadHash().equals(hash)) throw rule("APPLICATION_REQUEST_CONFLICT","This request key belongs to a different command.");
            return existing.get().result();
        }
        if(store.version(caregiverId)!=version) throw rule("APPLICATION_VERSION_CONFLICT","Refresh before saving: your application has changed.");
        mutation.apply(locked); store.advance(caregiverId);
        var result=view(owner,store.lockCaregiver(caregiverId));
        store.receipt(actor.id(),key,hash,result); return result;
    }
    private View view(AppUser user,Caregiver c) {
        var a=store.findByCaregiver(c.id()).orElse(null);
        boolean mutable=user.enabled() && c.status()==Caregiver.Status.ONBOARDING;
        boolean canWork=user.enabled() && c.isAssignable() && (a==null || a.status()==Status.APPROVED);
        return new View(c.id(),user.id(),user.username(),c.status().name(),c.phone(),store.version(c.id()),a,
                a==null ? List.of() : store.history(a.id()),canWork,mutable && a!=null && a.status()!=Status.APPROVED,
                mutable && a!=null && a.status()==Status.REJECTED,a==null ? (c.isAssignable() ? "LEGACY_ACTIVE" : "EXISTING_ONBOARDING") : null);
    }
    private AppUser actor(String username,Role role) { return users.findByUsername(username).filter(u -> u.enabled() && u.hasRole(role)).orElseThrow(CaregiverAdmissionService::denied); }
    private Caregiver profile(AppUser user) { return caregivers.findByUserId(user.id()).orElseThrow(() -> new ResourceNotFound("Caregiver profile",user.id())); }
    private CaregiverApplication application(Long caregiverId) { return store.findByCaregiver(caregiverId).orElseThrow(() -> new ResourceNotFound("Application for caregiver",caregiverId)); }
    private static void editable(Caregiver c) { if(c.status()!=Caregiver.Status.ONBOARDING) throw rule("APPLICATION_PROFILE_CHANGED","This profile can no longer be admitted or edited."); }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore"))).withNano(0); }
    private static AccessDeniedException denied() { return new AccessDeniedException("This account cannot perform that caregiver action."); }
    private static BusinessRuleViolation rule(String code,String message) { return new BusinessRuleViolation(code,message); }
    private static void canonicalKey(String key) {
        try { if(!UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); }
        catch(RuntimeException _) { throw rule("APPLICATION_REQUEST_INVALID","Use a canonical UUID request key."); }
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
