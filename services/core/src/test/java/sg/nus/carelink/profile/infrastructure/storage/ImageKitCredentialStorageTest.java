package sg.nus.carelink.profile.infrastructure.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage.Upload;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ImageKitCredentialStorageTest {
    static final String ENDPOINT = "https://ik.imagekit.io/demo", KEY = "private_test_key";
    static final String UPLOAD = "https://upload.imagekit.io/api/v1/files/upload", FILES = "https://api.imagekit.io/v1/files";
    static final String AUTH = "Basic " + Base64.getEncoder().encodeToString((KEY + ":").getBytes(StandardCharsets.UTF_8));
    static final String STORED = "ik:65f1a2b3c4d5e6f7a8b9c0d1:/carelink/credentials/0f8b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d.png";
    final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC);
    Path root;
    PrivateCredentialStorage local;
    MockRestServiceServer server;
    ImageKitCredentialStorage store;

    @BeforeEach void setup() throws Exception {
        Path parent = Path.of("../reports/main-sync/tmp"); Files.createDirectories(parent);
        root = Files.createTempDirectory(parent, "imagekit-storage-test-");
        local = new PrivateCredentialStorage(root.toString(), parent.toString());
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        store = new ImageKitCredentialStorage(local, clock, builder, ENDPOINT + "/", KEY, "carelink/credentials", UPLOAD, FILES);
    }
    @AfterEach void cleanup() throws Exception {
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }

    @Test void savesAsPrivateFileAndKeepsIdAndPath() throws Exception {
        var prepared = store.prepare(new Upload("first-aid.png", "image/png", PrivateCredentialStorageTest.picture("png")));
        server.expect(requestTo(UPLOAD)).andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(content().string(containsString("name=\"isPrivateFile\"")))
                .andExpect(content().string(containsString("name=\"folder\"")))
                .andRespond(request -> {
                    String body = new String(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsBytes(), StandardCharsets.ISO_8859_1);
                    assertThat(body).contains("\r\n\r\ntrue\r\n").contains("/carelink/credentials").doesNotContain("first-aid");
                    String name = formField(body, "fileName");
                    return withSuccess("{\"fileId\":\"65f1a2b3c4d5e6f7a8b9c0d1\",\"filePath\":\"/carelink/credentials/" + name + "\",\"size\":1}",
                            MediaType.APPLICATION_JSON).createResponse(request);
                });
        String key = store.save(prepared);
        assertThat(key).matches("ik:65f1a2b3c4d5e6f7a8b9c0d1:/carelink/credentials/[a-f0-9-]{36}\\.png");
        assertThat(store.available(key)).isTrue();
        server.verify();
    }

    /** The value of one text part of a multipart body: the line after the part's blank header separator. */
    static String formField(String body, String name) {
        int part = body.indexOf("name=\"" + name + "\"");
        int start = body.indexOf("\r\n\r\n", part) + 4;
        return body.substring(start, body.indexOf("\r\n", start));
    }

    @Test void unexpectedUploadReplyIsUnavailable() throws Exception {
        var prepared = store.prepare(new Upload("first-aid.png", "image/png", PrivateCredentialStorageTest.picture("png")));
        server.expect(requestTo(UPLOAD)).andRespond(withSuccess("{\"fileId\":\"../x\",\"filePath\":\"/elsewhere/a.png\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> store.save(prepared)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test void readsOriginalBytesThroughShortLivedSignedUrl() throws Exception {
        String path = "carelink/credentials/0f8b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d.png?tr=orig-true";
        long expires = 1_700_000_060;
        Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = HexFormat.of().formatHex(mac.doFinal((path + expires).getBytes(StandardCharsets.UTF_8)));
        byte[] bytes = {1, 2, 3};
        server.expect(requestTo(ENDPOINT + "/" + path + "&ik-t=" + expires + "&ik-s=" + signature)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(bytes, MediaType.IMAGE_PNG));
        assertThat(store.read(STORED)).isEqualTo(bytes);
        server.verify();
    }

    @Test void failedOrOversizedReadsAreUnavailable() {
        server.expect(requestTo(containsString("ik-s="))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(containsString("ik-s="))).andRespond(withSuccess(new byte[PrivateCredentialStorage.MAX_FILE_BYTES + 1], MediaType.IMAGE_PNG));
        for (int i = 0; i < 2; i++)
            assertThatThrownBy(() -> store.read(STORED)).isInstanceOfSatisfying(ResponseStatusException.class,
                    ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> store.read("ik:../../etc:/passwd")).isInstanceOf(ResponseStatusException.class);
        assertThat(store.available("ik:../../etc:/passwd")).isFalse();
    }

    @Test void discardDeletesByFileIdAndToleratesMissingFiles() {
        server.expect(requestTo(FILES + "/65f1a2b3c4d5e6f7a8b9c0d1")).andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH)).andRespond(withNoContent());
        server.expect(requestTo(FILES + "/65f1a2b3c4d5e6f7a8b9c0d1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(FILES + "/65f1a2b3c4d5e6f7a8b9c0d1")).andRespond(withServerError());
        for (int i = 0; i < 3; i++) assertThatCode(() -> store.discard(STORED)).doesNotThrowAnyException();
        server.verify();
    }

    @Test void filesSavedLocallyBeforeTheSwitchStayReadable() throws Exception {
        var prepared = local.prepare(new Upload("old.jpg", "image/jpeg", PrivateCredentialStorageTest.picture("jpg")));
        String key = local.save(prepared);
        assertThat(store.available(key)).isTrue();
        assertThat(store.read(key)).isEqualTo(prepared.bytes());
        store.discard(key);
        assertThat(local.available(key)).isFalse();
    }

    @Test void refusesToStartWithoutCredentialsOrWithUnsafeFolder() {
        var builder = RestClient.builder();
        assertThatThrownBy(() -> new ImageKitCredentialStorage(local, clock, builder, ENDPOINT, "", "carelink", UPLOAD, FILES))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ImageKitCredentialStorage(local, clock, builder, "http://ik.imagekit.io/demo", KEY, "carelink", UPLOAD, FILES))
                .isInstanceOf(IllegalStateException.class);
        for (String folder : List.of("", "/carelink", "../up", "a".repeat(101)))
            assertThatThrownBy(() -> new ImageKitCredentialStorage(local, clock, builder, ENDPOINT, KEY, folder, UPLOAD, FILES))
                    .isInstanceOf(IllegalStateException.class);
    }
}
