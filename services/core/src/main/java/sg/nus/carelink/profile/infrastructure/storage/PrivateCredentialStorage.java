package sg.nus.carelink.profile.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage;
import sg.nus.carelink.profile.domain.service.CredentialDigest;

@Component
public class PrivateCredentialStorage implements CredentialDocumentStorage {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger(PrivateCredentialStorage.class);
    private final Path root;
    private final Path validationRoot;
    private final Semaphore pdfSlots = new Semaphore(2);

    public PrivateCredentialStorage(@Value("${carelink.caregiver.credential-storage:/var/lib/carelink/private/credentials}") String directory,
            @Value("${spring.servlet.multipart.location:../reports/main-sync/tmp}") String multipartDirectory) {
        root = Path.of(directory).toAbsolutePath().normalize();
        validationRoot = Path.of(multipartDirectory).toAbsolutePath().normalize();
        try {
            Path multipart = Path.of(multipartDirectory).toAbsolutePath().normalize();
            checkAncestors(multipart);
            Files.createDirectories(multipart);
            checkAncestors(multipart);
        }
        catch (IOException ex) { throw new IllegalStateException("The private multipart directory must be writable."); }
    }

    @Override public Prepared prepare(Upload upload) {
        String name = upload.fileName();
        if (name == null || name.isBlank() || name.length() > 255 || name.chars().anyMatch(c -> c < 32 || c == 127)
                || name.contains("/") || name.contains("\\") || name.contains(":")) throw invalid("Use a simple document filename without paths.");
        byte[] original = upload.bytes();
        if (original == null || original.length == 0 || original.length > MAX_FILE_BYTES) throw invalid("Each document must be non-empty and at most 5 MiB.");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = switch (extension) { case "jpg", "jpeg" -> "image/jpeg"; case "png" -> "image/png"; case "pdf" -> "application/pdf"; default -> throw invalid("Choose a JPEG, PNG or PDF document."); };
        if (upload.contentType() != null && !upload.contentType().equals(type)) throw invalid("The document type does not match its filename.");
        byte[] stored;
        if ("application/pdf".equals(type)) { validatePdf(original); stored = original; }
        else stored = image(original, type);
        if (stored.length > MAX_FILE_BYTES) throw invalid("The processed image exceeds 5 MiB. Choose a smaller image.");
        return new Prepared(name.strip(), type, stored, CredentialDigest.sha256(original));
    }

    private byte[] image(byte[] bytes, String type) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("The image could not be decoded.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!("image/png".equals(type) ? format.equals("png") : format.equals("jpeg") || format.equals("jpg")))
                    throw invalid("The actual image type does not match the filename.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 20_000_000) throw invalid("Choose an image no larger than 20 megapixels.");
                BufferedImage decoded = reader.read(0);
                var output = new ByteArrayOutputStream();
                // Explicit memory output prevents ImageIO's default cache from writing outside the private root.
                try (var imageOutput = new javax.imageio.stream.MemoryCacheImageOutputStream(output)) {
                    if (!ImageIO.write(decoded, format.equals("png") ? "png" : "jpeg", imageOutput)) throw invalid("Unsupported image encoding.");
                }
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw invalid("The image is damaged or unreadable."); }
    }

    private void validatePdf(byte[] bytes) {
        if (!pdfSlots.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Document validation is busy. Retry the same request later.");
        Path temporary = null;
        Process process = null;
        try {
            ensureValidationRoot();
            temporary = Files.createTempFile(validationRoot, "pdf-check-", ".pdf");
            Files.write(temporary, bytes);
            String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            File source = new ApplicationHome(CredentialPdfCheck.class).getSource();
            String classpath = source != null && source.isFile() && source.getName().endsWith(".jar") ? source.getAbsolutePath()
                    : System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            String main = source != null && source.isFile() && source.getName().endsWith(".jar")
                    ? "org.springframework.boot.loader.launch.PropertiesLauncher" : CredentialPdfCheck.class.getName();
            process = new ProcessBuilder(java, "-Xmx96m", "-Xss512k", "-XX:-UsePerfData", "-Djava.io.tmpdir=" + validationRoot,
                    "-Dloader.main=" + CredentialPdfCheck.class.getName(), "-cp", classpath, main, temporary.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) throw invalid("The PDF is unreadable or unsupported. Use a simple, unencrypted PDF of at most 20 pages without actions or attachments.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Document validation was interrupted.");
        } catch (IOException ex) { throw unavailable(); }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(1, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ex) { LOG.warn("Temporary certificate validation cleanup deferred"); }
            pdfSlots.release();
        }
    }

    private void ensureValidationRoot() throws IOException { checkAncestors(validationRoot); Files.createDirectories(validationRoot); checkAncestors(validationRoot); }

    private void ensureRoot() throws IOException {
        checkAncestors(root);
        Files.createDirectories(root);
        checkAncestors(root);
    }

    /** Reject directory links/junctions before creating anything through them. */
    private static void checkAncestors(Path directory) throws IOException {
        Path cursor = directory;
        while (cursor != null) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor)))
                throw new IOException("Redirected private storage directory");
            cursor = cursor.getParent();
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}\\.(jpg|png|pdf)")) throw unavailable();
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root) || Files.isSymbolicLink(path)) throw unavailable();
        return path;
    }

    @Override public String save(Prepared document) {
        String extension = switch (document.contentType()) { case "image/png" -> "png"; case "application/pdf" -> "pdf"; default -> "jpg"; };
        String key = UUID.randomUUID() + "." + extension;
        try {
            ensureRoot();
            try (var channel = java.nio.channels.FileChannel.open(resolve(key), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                var bytes = java.nio.ByteBuffer.wrap(document.bytes());
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            return key;
        }
        catch (IOException ex) { discard(key); throw unavailable(); }
    }

    @Override public byte[] read(String key) {
        try {
            ensureRoot(); Path path = resolve(key);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) throw unavailable();
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) throw unavailable();
                return bytes;
            }
        } catch (IOException ex) { throw unavailable(); }
    }
    @Override public boolean available(String key) {
        try { ensureRoot(); return Files.isRegularFile(resolve(key), LinkOption.NOFOLLOW_LINKS); }
        catch (IOException | ResponseStatusException ex) { return false; }
    }
    @Override public void discard(String key) {
        try { ensureRoot(); Files.deleteIfExists(resolve(key)); }
        catch (IOException | ResponseStatusException ex) { LOG.warn("Rolled-back certificate cleanup deferred; private orphan reconciliation required"); }
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The document store is temporarily unavailable."); }
}
