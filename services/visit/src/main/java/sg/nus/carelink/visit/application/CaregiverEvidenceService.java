package sg.nus.carelink.visit.application;

import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

import sg.nus.carelink.shared.error.*;
import sg.nus.carelink.visit.domain.model.*;
import sg.nus.carelink.visit.domain.repository.*;

@Service
public class CaregiverEvidenceService {
    private static final String ACTION="EVIDENCE_UPLOAD";
    private final CaregiverCommandExecutor executor;
    private final CaregiverWorkDirectory directory;
    private final VisitRepository visits;
    private final VisitCommandRepository commands;
    private final VisitTaskRepository tasks;
    private final VisitEvidenceRepository evidence;
    private final VisitEvidenceFileStore metadata;
    private final EvidenceImageStorage images;
    private final CaregiverCommandStore receipts;
    private final int maxFiles;
    private final VisitEvents events;
    public CaregiverEvidenceService(CaregiverCommandExecutor executor,CaregiverWorkDirectory directory,VisitRepository visits,
            VisitCommandRepository commands,VisitTaskRepository tasks,VisitEvidenceRepository evidence,VisitEvidenceFileStore metadata,
            EvidenceImageStorage images,CaregiverCommandStore receipts,VisitEvents events,@Value("${carelink.caregiver.evidence-max-files:20}") int maxFiles) {
        this.executor=executor; this.directory=directory; this.visits=visits; this.commands=commands; this.tasks=tasks;
        this.evidence=evidence; this.metadata=metadata; this.images=images; this.receipts=receipts; this.events=events;
        if(maxFiles<1 || maxFiles>20) throw new IllegalArgumentException("Evidence count limit must be 1–20."); this.maxFiles=maxFiles;
    }
    public Saved upload(String username,Long visitId,VisitEvidence.Kind kind,Long taskId,Integer version,UUID requestId,String fileName,String type,byte[] bytes) {
        readable(username,visitId);
        if(kind!=VisitEvidence.Kind.PHOTO && kind!=VisitEvidence.Kind.SIGNATURE) throw invalid("Only PHOTO and SIGNATURE images are supported.");
        if(version==null || version<0 || requestId==null) throw invalid("A version and request key are required.");
        var image=images.prepare(fileName,type,bytes);
        String fingerprint=CommandFingerprint.of(visitId,version,kind,taskId,image.fileName(),image.contentType(),image.originalHash());
        return executor.execute(username,visitId,ACTION,null,(actor,visit)->{
            if(visit.status()==Visit.Status.CANCELLED) throw new BusinessRuleViolation("VISIT_CANCELLED","This visit was cancelled.");
            var replay=receipts.find(actor.userId(),requestId);
            if(replay.isPresent()) {
                replay.get().requireSame(ACTION,visitId,fingerprint);
                var original=metadata.find(visitId,replay.get().resultId()).orElseThrow(()->new ResourceNotFound("Evidence",replay.get().resultId()));
                return new Saved(view(original),replay.get().version(),true);
            }
            CaregiverCommandExecutor.version(visit,version);
            if(visit.status()!=Visit.Status.IN_PROGRESS || visit.checkedInAt()==null) throw new BusinessRuleViolation("EVIDENCE_VISIT_NOT_IN_PROGRESS","Evidence can only be uploaded after check-in and before check-out.");
            if(taskId!=null && tasks.findById(taskId).filter(t->visitId.equals(t.visitId())).isEmpty()) throw invalid("The selected task does not belong to this visit.");
            if(metadata.fileCount(visitId)>=maxFiles) throw new BusinessRuleViolation("EVIDENCE_LIMIT_REACHED","This visit has reached its evidence file limit.");
            String key=images.save(image);
            // Files precede database references. Only confirmed rollback permits cleanup;
            // an unknown commit result retains the file for database reconciliation.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if(status==STATUS_ROLLED_BACK) images.discard(key); }
            });
            var now=executor.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var saved=evidence.save(new VisitEvidence(null,visitId,kind,key,VisitEvidence.VerificationStatus.UNVERIFIED,now));
            var file=new VisitEvidenceFile(saved.id(),actor.userId(),key,image.contentType(),image.bytes().length,image.storedHash(),image.originalHash(),image.fileName(),taskId,now);
            metadata.save(file);
            var changed=commands.save(visit); // version only; never invent a state transition or finish a task
            receipts.audit(actor.userId(),visitId,ACTION,"OK","UNVERIFIED");
            receipts.save(new CaregiverCommandReceipt(actor.userId(),requestId,ACTION,visitId,fingerprint,saved.id(),changed.version(),now));
            events.evidenceUpdated(changed,(int)metadata.count(visitId),(int)metadata.verifiedCount(visitId));
            return new Saved(view(new VisitEvidenceFileStore.Entry(saved,file)),changed.version(),false);
        });
    }
    public Page list(String username,Long visitId,int page,int size) {
        readable(username,visitId);
        if(page<0 || page>10000 || size<1 || size>50) throw invalid("Choose a valid page and size (1–50).");
        var rows=metadata.list(visitId,page*size,size).stream().map(this::view).toList();
        readable(username,visitId);
        return new Page(rows,page,size,metadata.count(visitId),maxFiles);
    }
    public Content content(String username,Long visitId,Long evidenceId) {
        readable(username,visitId);
        var item=metadata.find(visitId,evidenceId).orElseThrow(()->new ResourceNotFound("Evidence",evidenceId));
        if(item.file()==null) throw new ResourceNotFound("Evidence file",evidenceId);
        var bytes=images.read(item.file().storageKey());
        if(bytes.length!=item.file().byteSize() || !hash(bytes).equals(item.file().sha256())) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"The evidence file is temporarily unavailable.");
        var actor=readable(username,visitId); // stale links confer no enduring rights
        receipts.audit(actor.userId(),visitId,"EVIDENCE_READ","OK","AUTHORIZED");
        return new Content(view(item),bytes);
    }
    /** CG06: only the active account's own immutable upload; no other work-pack data. */
    public Content historicalContent(String username,Long visitId,Long evidenceId) {
        var actor=directory.require(username).requireWorking();
        var item=metadata.find(visitId,evidenceId).filter(e->e.file()!=null && actor.userId().equals(e.file().uploaderUserId()))
                .orElseThrow(()->new ResourceNotFound("Own evidence",evidenceId));
        var bytes=images.read(item.file().storageKey());
        if(bytes.length!=item.file().byteSize() || !hash(bytes).equals(item.file().sha256())) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"The evidence file is temporarily unavailable.");
        if(!directory.require(username).requireWorking().userId().equals(actor.userId())) throw new AccessDeniedException("Account changed");
        receipts.audit(actor.userId(),visitId,"OWN_EVIDENCE_READ","OK","UPLOADER");
        return new Content(view(item),bytes);
    }
    private CaregiverWorkDirectory.Profile readable(String username,Long id) {
        // New private-file access does not introduce an offboard retention/access policy.
        var actor=directory.require(username).requireWorking();
        var visit=visits.findById(id).orElseThrow(()->new ResourceNotFound("Visit",id));
        if(!Objects.equals(visit.caregiverId(),actor.id())) throw new AccessDeniedException("Not assigned");
        if(visit.status()==Visit.Status.CANCELLED) throw new BusinessRuleViolation("VISIT_CANCELLED","This visit was cancelled.");
        return actor;
    }
    private View view(VisitEvidenceFileStore.Entry entry) {
        var e=entry.evidence(); var f=entry.file(); boolean available=f!=null && images.available(f.storageKey());
        return new View(e.id(),e.visitId(),e.kind().name(),e.verificationStatus().name(),e.capturedAt(),f==null ? null : f.taskId(),
                f==null ? null : f.fileName(),f==null ? null : f.contentType(),f==null ? null : f.byteSize(),available,
                f==null ? null : "/api/visits/"+e.visitId()+"/evidence/"+e.id()+"/content");
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException invalid(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
    public record View(Long id,Long visitId,String kind,String verificationStatus,LocalDateTime capturedAt,Long taskId,String fileName,String contentType,Long byteSize,boolean available,String downloadPath) { }
    public record Saved(View evidence,Integer visitVersion,boolean replayed) { }
    public record Page(List<View> items,int page,int size,long total,int maxFiles) { }
    public record Content(View metadata,byte[] bytes) {
        public Content { bytes=bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Content value && Objects.equals(metadata, value.metadata) && Arrays.equals(bytes, value.bytes);
        }
        @Override public int hashCode() { return Objects.hash(metadata, Arrays.hashCode(bytes)); }
        @Override public String toString() { return "Private evidence content"; }
    }
}
