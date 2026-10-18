package sg.nus.carelink.profile.application;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.domain.model.*;
import sg.nus.carelink.profile.domain.repository.*;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage.*;
import sg.nus.carelink.profile.domain.repository.CredentialSubmissionStore.Result;
import sg.nus.carelink.profile.domain.service.CredentialDigest;
import sg.nus.carelink.profile.domain.service.CredentialResubmissionHistory;
import sg.nus.carelink.profile.domain.service.CaregiverCredentialAlertPolicy;
import sg.nus.carelink.shared.error.*;
import sg.nus.carelink.shared.security.Role;

/** Own immutable submissions only; MG06 retains ownership of review and onboarding. */
@Service
public class CaregiverCredentialService {
    public record Request(Long credentialTypeId, String certificateNo, String issuingBody,
            LocalDate validFrom, LocalDate expiryDate, boolean noExpiry, String clientRequestId) { }
    public record DocumentView(Long id, Long credentialId, String fileName, String contentType,
            long byteSize, LocalDateTime uploadedAt, boolean contentAvailable, String contentUrl) { }
    public record View(Long id, Long credentialTypeId, String credentialTypeName, String certificateNo,
            String issuingBody, LocalDate validFrom, LocalDate expiryDate, String status,
            LocalDateTime submittedAt, String reviewNote, LocalDateTime reviewedAt,
            Long renewsCredentialId, Long resubmitsCredentialId, boolean historical, List<DocumentView> documents) { }
    public record Page(List<View> items, int page, int size, long total) { }
    public record Content(DocumentView document, byte[] bytes) {
        public Content { bytes = bytes == null ? null : bytes.clone(); }
        @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Content value && Objects.equals(document, value.document) && Arrays.equals(bytes, value.bytes);
        }
        @Override public int hashCode() { return Objects.hash(document, Arrays.hashCode(bytes)); }
        @Override public String toString() { return "Content[document=" + document + ", bytesHash=" + Arrays.hashCode(bytes) + "]"; }
    }
    private record Actor(Long userId, Caregiver caregiver) { }
    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private final UserDirectory users;
    private final CaregiverRepository caregivers;
    private final CredentialRepository credentials;
    private final CredentialSubmissionStore store;
    private final CredentialDocumentStorage files;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CaregiverCredentialService(UserDirectory users, CaregiverRepository caregivers,
            CredentialRepository credentials, CredentialSubmissionStore store,
            CredentialDocumentStorage files, Clock clock, PlatformTransactionManager transactions) {
        this.users = users; this.caregivers = caregivers; this.credentials = credentials;
        this.store = store; this.files = files; this.clock = clock;
        transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public List<CredentialType> types(String username) { actor(username, false); return store.types(); }

    public Page list(String username, int page, int size) {
        Actor actor = actor(username, false);
        if (page < 0 || size < 1 || size > 50) throw invalid("Use page >= 0 and size between 1 and 50.");
        List<Credential> rows = credentials.findByCaregiverId(actor.caregiver().id()).reversed();
        Map<Long, String> names = new HashMap<>(); store.types().forEach(type -> names.put(type.id(), type.name()));
        Map<Long, Long> retries = store.resubmissions(actor.caregiver().id());
        List<View> items = rows.stream().skip((long) page * size).limit(size)
                .map(row -> view(row, names.getOrDefault(row.credentialTypeId(), "Credential"), retries.get(row.id()), historical(row, rows, retries))).toList();
        return new Page(items, page, size, rows.size());
    }

    public View detail(String username, Long id) {
        Actor actor = actor(username, false);
        Credential row = own(id, actor.caregiver().id());
        String name = store.types().stream().filter(type -> type.id().equals(row.credentialTypeId()))
                .map(CredentialType::name).findFirst().orElse("Credential");
        var retries = store.resubmissions(row.caregiverId());
        return view(row, name, retries.get(id), historical(row, credentials.findByCaregiverId(row.caregiverId()), retries));
    }

    private boolean historical(Credential c, List<Credential> rows, Map<Long, Long> retries) {
        return c.status() == Credential.Status.REVOKED
                || CredentialResubmissionHistory.forAlerts(rows, retries).stream().noneMatch(row -> row.id().equals(c.id()))
                || rows.stream().anyMatch(child -> c.id().equals(child.renewsCredentialId()) && child.isApproved()
                    && child.validFrom() != null && !child.validFrom().isAfter(LocalDate.now(clock.withZone(SGT)))
                    && c.credentialTypeId().equals(child.credentialTypeId()));
    }

    private View view(Credential c, String typeName, Long retry, boolean historical) {
        return new View(c.id(), c.credentialTypeId(), typeName, c.certificateNo(), c.issuingBody(), c.validFrom(),
                Credential.PERMANENT.equals(c.expiryDate()) ? null : c.expiryDate(), c.status().name(), c.createdAt(),
                c.reviewNote(), c.reviewedAt(), c.renewsCredentialId(), retry, historical, store.documents(c.id()).stream().map(this::documentView).toList());
    }

    public List<DocumentView> documents(String username, Long id) {
        readable(username, id);
        return store.documents(id).stream().map(this::documentView).toList();
    }

    public Content content(String username, Long id, Long documentId) {
        readable(username, id);
        CredentialDocument document = store.documents(id).stream().filter(d -> d.id().equals(documentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFound("Credential document", documentId));
        byte[] bytes = files.read(document.storageKey());
        if (bytes.length != document.byteSize() || !CredentialDigest.sha256(bytes).equals(document.sha256()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The document is temporarily unavailable.");
        // Recheck after reading: a previously authorized URL is not an enduring grant.
        Long actorId = readable(username, id);
        store.audit(actorId, "CREDENTIAL_DOCUMENT_READ", id, now());
        return new Content(documentView(document), bytes);
    }

    private DocumentView documentView(CredentialDocument d) {
        return new DocumentView(d.id(), d.credentialId(), d.fileName(), d.contentType(), d.byteSize(), d.uploadedAt(),
                files.available(d.storageKey()), "/api/credentials/" + d.credentialId() + "/documents/" + d.id() + "/content");
    }

    public Result submit(String username, String action, Long sourceId, Request request, List<Upload> uploads) {
        Actor initial = actor(username, true);
        String requestId = requestId(request.clientRequestId());
        if (uploads == null || uploads.isEmpty() || uploads.size() > 3) throw invalid("Select between 1 and 3 documents.");
        List<Prepared> prepared = uploads.stream().map(files::prepare).toList();
        Request normalized = normalize(request);
        String hash = fingerprint(action, sourceId, normalized, prepared);
        List<String> savedKeys = new ArrayList<>();
        return Objects.requireNonNull(transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int completion) {
                        if (completion == STATUS_ROLLED_BACK) savedKeys.forEach(files::discard);
                        // UNKNOWN is not proof of rollback: retain files for reconciliation.
                    }
                });
                LocalDateTime at = now();
                store.lockCaregiver(initial.caregiver().id(), at);
                Actor acting = actor(username, true);
                if (!acting.caregiver().id().equals(initial.caregiver().id())) throw new AccessDeniedException("Profile changed.");
                var replay = store.receipt(acting.userId(), requestId);
                if (replay.isPresent()) {
                    if (!replay.get().payloadHash().equals(hash)) throw rule("REQUEST_KEY_REUSED", "This request key belongs to a different submission.");
                    return replay.get().result();
                }
                Credential source = sourceId == null ? null : own(sourceId, acting.caregiver().id());
                Long typeId = source == null ? normalized.credentialTypeId() : source.credentialTypeId();
                if (typeId == null || store.types().stream().noneMatch(type -> type.id().equals(typeId))) throw invalid("Choose an existing credential type.");
                if (source != null && normalized.credentialTypeId() != null && !typeId.equals(normalized.credentialTypeId()))
                    throw invalid("The credential type cannot change for a renewal or resubmission.");
                List<Credential> rows = credentials.findByCaregiverId(acting.caregiver().id());
                if (rows.stream().anyMatch(c -> typeId.equals(c.credentialTypeId()) && c.awaitsReview()))
                    throw rule("CREDENTIAL_ALREADY_PENDING", "A certificate of this type is already awaiting review.");
                Long renewsId = target(action, source, rows, typeId);
                LocalDate today = LocalDate.now(clock.withZone(SGT));
                LocalDate expiry = normalized.noExpiry() ? Credential.PERMANENT : normalized.expiryDate();
                if (expiry == null || (!normalized.noExpiry() && expiry.equals(Credential.PERMANENT))
                        || expiry.isBefore(normalized.validFrom()) || expiry.isBefore(today))
                    throw invalid("Choose a valid expiry on or after the start date and today, or explicitly select No expiry.");
                Long retryId = "RESUBMIT".equals(action) ? sourceId : null;
                Long id = store.insertCredential(new Credential(null, acting.caregiver().id(), typeId, null,
                        normalized.certificateNo(), normalized.issuingBody(), normalized.validFrom(), expiry,
                        Credential.Status.SUBMITTED, at, at, renewsId));
                List<Long> documentIds = new ArrayList<>();
                for (int i = 0; i < prepared.size(); i++) {
                    Prepared document = prepared.get(i);
                    String key = files.save(document); savedKeys.add(key);
                    documentIds.add(store.insertDocument(new CredentialDocument(null, id, key, document.fileName(),
                            document.contentType(), document.bytes().length, CredentialDigest.sha256(document.bytes()), acting.userId(), at, i)));
                }
                Result result = new Result(id, "SUBMITTED", at, List.copyOf(documentIds));
                store.saveSubmission(acting.userId(), requestId, action, retryId, hash, result);
                store.audit(acting.userId(), "CREDENTIAL_" + action, id, at);
                return result;
            }));
    }

    private Long target(String action, Credential source, List<Credential> rows, Long typeId) {
        if ("NEW".equals(action)) {
            requireNew(rows, typeId);
            return null;
        }
        if (source == null) throw invalid("A source credential is required.");
        Credential parent;
        if ("RESUBMIT".equals(action)) {
            if (source.status() != Credential.Status.REJECTED) throw rule("CREDENTIAL_NOT_REJECTED", "Only a rejected submission can be resubmitted.");
            if (store.resubmissions(source.caregiverId()).containsValue(source.id()))
                throw rule("CREDENTIAL_ALREADY_RESUBMITTED", "This rejected submission has already been resubmitted.");
            if (source.renewsCredentialId() == null) { requireNew(rows, typeId); return null; }
            parent = own(source.renewsCredentialId(), source.caregiverId());
        } else if ("RENEWAL".equals(action)) parent = source;
        else throw invalid("Unsupported submission action.");
        if (!parent.isApproved() || !typeId.equals(parent.credentialTypeId()))
            throw rule("INVALID_RENEWAL", "Renew an approved credential of the same type.");
        List<Credential> graph = CredentialResubmissionHistory.forAlerts(rows, store.resubmissions(source.caregiverId()));
        if (new CaregiverCredentialAlertPolicy().evaluate(source.caregiverId(),
                graph.stream().filter(c -> typeId.equals(c.credentialTypeId())).toList(),
                LocalDate.now(clock.withZone(SGT)), 30).reviewRequired())
            throw rule("CREDENTIAL_REVIEW_REQUIRED", "The credential chain needs your manager's review before renewal.");
        long terminalCount = rows.stream().filter(c -> typeId.equals(c.credentialTypeId()) && c.isApproved())
                .filter(c -> rows.stream().noneMatch(child -> c.id().equals(child.renewsCredentialId()) && child.isApproved())).count();
        if (terminalCount > 1) throw rule("CREDENTIAL_REVIEW_REQUIRED", "Multiple credential chains need your manager's review before renewal.");
        if (rows.stream().anyMatch(c -> parent.id().equals(c.renewsCredentialId()) && c.isApproved()))
            throw rule("RENEWAL_SUPERSEDED", "This credential already has an approved replacement. Open that replacement instead.");
        if (graph.stream().anyMatch(c -> parent.id().equals(c.renewsCredentialId()) && !c.id().equals(source.id())))
            throw rule("CREDENTIAL_REVIEW_REQUIRED", "An existing renewal must be resubmitted or reviewed before another renewal.");
        return parent.id();
    }

    private void requireNew(List<Credential> rows, Long typeId) {
        if (rows.stream().anyMatch(c -> typeId.equals(c.credentialTypeId()) && c.isApproved()))
            throw rule("RENEWAL_REQUIRED", "Use Renew certificate for an existing approved credential.");
    }

    private Request normalize(Request r) {
        String issuer = r.issuingBody() == null ? "" : r.issuingBody().strip();
        String number = r.certificateNo() == null ? null : r.certificateNo().strip();
        if (issuer.isBlank() || issuer.length() > 150 || (number != null && number.length() > 100) || r.validFrom() == null
                || r.validFrom().getYear() < 1000 || r.validFrom().getYear() > 9999)
            throw invalid("An issuing body (up to 150 characters) and valid start date are required. Certificate number has at most 100 characters.");
        if (r.noExpiry() && r.expiryDate() != null) throw invalid("Do not provide an expiry when No expiry is selected.");
        if (r.expiryDate() != null && (r.expiryDate().getYear() < 1000 || r.expiryDate().getYear() > 9999))
            throw invalid("Use an expiry year between 1000 and 9999.");
        return new Request(r.credentialTypeId(), number == null || number.isEmpty() ? null : number,
                issuer, r.validFrom(), r.expiryDate(), r.noExpiry(), r.clientRequestId());
    }

    private String fingerprint(String action, Long source, Request r, List<Prepared> uploads) {
        List<Object> fields = new ArrayList<>(Arrays.asList(action, source, r.credentialTypeId(), r.certificateNo(),
                r.issuingBody(), r.validFrom(), r.expiryDate(), r.noExpiry()));
        uploads.forEach(file -> { fields.add(file.fileName()); fields.add(file.contentType()); fields.add(file.originalHash()); });
        StringBuilder canonical = new StringBuilder();
        fields.forEach(field -> {
            if (field == null) canonical.append("N;");
            else { String value = field.toString(); canonical.append('V').append(value.length()).append(':').append(value); }
        });
        return CredentialDigest.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Actor actor(String username, boolean writing) {
        var user = users.findByUsername(username).filter(u -> u.enabled() && u.hasRole(Role.CAREGIVER))
                .orElseThrow(() -> new AccessDeniedException("A caregiver account is required."));
        Caregiver caregiver = caregivers.findByUserId(user.id()).orElseThrow(() -> new ResourceNotFound("Caregiver profile", user.id()));
        if (writing && caregiver.status() == Caregiver.Status.INACTIVE) throw new AccessDeniedException("An active caregiver profile is required.");
        return new Actor(user.id(), caregiver);
    }

    private Long readable(String username, Long id) {
        var user = users.findByUsername(username).filter(u -> u.enabled()).orElseThrow(() -> new AccessDeniedException("An enabled account is required."));
        if (user.hasRole(Role.MANAGER)) {
            credentials.findById(id).orElseThrow(() -> new ResourceNotFound("Credential", id));
        } else {
            Actor actor = actor(username, false); own(id, actor.caregiver().id());
        }
        return user.id();
    }

    private Credential own(Long id, Long caregiverId) {
        return credentials.findById(id).filter(c -> c.caregiverId().equals(caregiverId))
                .orElseThrow(() -> new ResourceNotFound("Credential", id));
    }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(SGT)).withNano(0); }
    private static String requestId(String value) {
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equals(value)) throw new IllegalArgumentException();
            return canonical;
        }
        catch (RuntimeException ex) { throw invalid("Use a canonical UUID request key."); }
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static BusinessRuleViolation rule(String code, String message) { return new BusinessRuleViolation(code, message); }
}
