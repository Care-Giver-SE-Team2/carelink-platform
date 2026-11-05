package sg.nus.carelink.profile.infrastructure.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps certificate files as private ImageKit files, readable only through a short-lived signed URL
 * fetched by the backend, so access checks and auditing stay in CaregiverCredentialService.
 * Validation still runs locally in PrivateCredentialStorage, and files saved before the switch
 * (plain "uuid.ext" keys) are still served from the local store.
 */
@Component
@Primary
@ConditionalOnBooleanProperty("carelink.caregiver.imagekit.enabled")
public class ImageKitCredentialStorage implements CredentialDocumentStorage {
    private static final Logger LOG = LoggerFactory.getLogger(ImageKitCredentialStorage.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern STORAGE_KEY = Pattern.compile("ik:([A-Za-z0-9]{1,64}):((?:/[A-Za-z0-9_-]{1,64}){1,8}/[a-f0-9-]{36}\\.(?:jpg|png|pdf))");
    private static final Pattern FOLDER_PATH = Pattern.compile("[A-Za-z0-9_-]{1,64}(?:/[A-Za-z0-9_-]{1,64}){0,6}");
    private static final long URL_SECONDS = 60;
    private final PrivateCredentialStorage local;
    private final RestClient http;
    private final Clock clock;
    private final String urlEndpoint;
    private final String privateKey;
    private final String folder;
    private final String uploadUrl;
    private final String filesUrl;

    @Autowired
    public ImageKitCredentialStorage(PrivateCredentialStorage local, Clock clock,
            @Value("${carelink.caregiver.imagekit.url-endpoint:}") String urlEndpoint,
            @Value("${carelink.caregiver.imagekit.private-key:}") String privateKey,
            @Value("${carelink.caregiver.imagekit.folder:carelink/credentials}") String folder,
            @Value("${carelink.caregiver.imagekit.upload-url:https://upload.imagekit.io/api/v1/files/upload}") String uploadUrl,
            @Value("${carelink.caregiver.imagekit.files-url:https://api.imagekit.io/v1/files}") String filesUrl) {
        this(local, clock, RestClient.builder().requestFactory(timeouts()), urlEndpoint, privateKey, folder, uploadUrl, filesUrl);
    }

    ImageKitCredentialStorage(PrivateCredentialStorage local, Clock clock, RestClient.Builder http, String urlEndpoint,
            String privateKey, String folder, String uploadUrl, String filesUrl) {
        if (urlEndpoint == null || !urlEndpoint.startsWith("https://") || privateKey == null || privateKey.isBlank())
            throw new IllegalStateException("ImageKit storage needs an https url-endpoint and a private key.");
        // credential_document.storage_key holds 255 characters: "ik:" + file id + ":/" + folder + "/" + file name.
        if (folder == null || folder.length() > 100 || !FOLDER_PATH.matcher(folder).matches())
            throw new IllegalStateException("The ImageKit folder must be a simple relative path of at most 100 characters.");
        this.local = local; this.clock = clock; this.http = http.build();
        this.urlEndpoint = withoutTrailingSlashes(urlEndpoint);
        this.privateKey = privateKey; this.folder = folder;
        this.uploadUrl = uploadUrl; this.filesUrl = withoutTrailingSlashes(filesUrl);
    }

    private static String withoutTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') end--;
        return url.substring(0, end);
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    @Override public Prepared prepare(Upload upload) { return local.prepare(upload); }

    @Override public String save(Prepared document) {
        String extension = switch (document.contentType()) { case "image/png" -> "png"; case "application/pdf" -> "pdf"; default -> "jpg"; };
        String name = UUID.randomUUID() + "." + extension;
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(document.bytes()) { @Override public String getFilename() { return name; } });
        form.add("fileName", name);
        form.add("folder", "/" + folder);
        form.add("useUniqueFileName", "false");
        form.add("overwriteFile", "false");
        form.add("isPrivateFile", "true");
        try {
            var reply = JSON.readTree(http.post().uri(URI.create(uploadUrl)).headers(this::authorize)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(String.class));
            String key = "ik:" + reply.path("fileId").asString("") + ":" + reply.path("filePath").asString("");
            if (key.length() <= 255 && STORAGE_KEY.matcher(key).matches()) return key;
            LOG.warn("ImageKit returned an unexpected upload reply; private orphan reconciliation required");
            throw unavailable();
        } catch (RestClientResponseException ex) {
            // ImageKit's error body is a short message (e.g. a rejected key); it never echoes the key.
            LOG.warn("ImageKit upload failed: HTTP {} {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw unavailable();
        } catch (RestClientException | JacksonException ex) {
            LOG.warn("ImageKit upload failed: {}", ex.toString());
            throw unavailable();
        }
    }

    @Override public byte[] read(String storageKey) {
        if (!storageKey.startsWith("ik:")) return local.read(storageKey);
        String path = parse(storageKey).group(2).substring(1) + "?tr=orig-true";
        long expires = clock.instant().getEpochSecond() + URL_SECONDS;
        URI url = URI.create(urlEndpoint + "/" + path + "&ik-t=" + expires + "&ik-s=" + sign(path + expires));
        try {
            return http.get().uri(url).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    LOG.warn("ImageKit read failed: HTTP {}", response.getStatusCode().value());
                    throw unavailable();
                }
                try (var input = response.getBody()) {
                    byte[] bytes = input.readNBytes(PrivateCredentialStorage.MAX_FILE_BYTES + 1);
                    if (bytes.length > PrivateCredentialStorage.MAX_FILE_BYTES) throw unavailable();
                    return bytes;
                }
            });
        } catch (RestClientException ex) {
            LOG.warn("ImageKit read failed: {}", ex.toString());
            throw unavailable();
        }
    }

    /** No network call: this runs for every document in a list; read() still fails closed when a file is gone. */
    @Override public boolean available(String storageKey) {
        if (!storageKey.startsWith("ik:")) return local.available(storageKey);
        return STORAGE_KEY.matcher(storageKey).matches();
    }

    @Override public void discard(String storageKey) {
        if (!storageKey.startsWith("ik:")) { local.discard(storageKey); return; }
        try {
            http.delete().uri(URI.create(filesUrl + "/" + parse(storageKey).group(1))).headers(this::authorize).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound _) { /* already gone */ }
        catch (RestClientException | ResponseStatusException _) {
            LOG.warn("Rolled-back certificate cleanup deferred; private orphan reconciliation required");
        }
    }

    private Matcher parse(String storageKey) {
        Matcher key = STORAGE_KEY.matcher(storageKey);
        if (!key.matches()) throw unavailable();
        return key;
    }

    private void authorize(HttpHeaders headers) { headers.setBasicAuth(privateKey, ""); }

    /** ImageKit's signed-URL scheme: hex HMAC-SHA1 of the URL after the endpoint, followed by the expiry. */
    @SuppressWarnings("java:S4790") // ImageKit only accepts HMAC-SHA1; keyed, so SHA-1 collisions don't weaken it
    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(privateKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException _) { throw unavailable(); }
    }

    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The document store is temporarily unavailable."); }
}
