package sg.nus.carelink.visit.infrastructure.storage;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import sg.nus.carelink.visit.domain.repository.EvidenceImageStorage;

/** JPEG/PNG only, bounded decode and re-encoding; no EXIF or public/static path. */
@Component
public class PrivateEvidenceImages implements EvidenceImageStorage {
    private final Path root;
    private final int maxBytes;
    private final long maxPixels;
    public PrivateEvidenceImages(@Value("${carelink.caregiver.evidence-storage:/var/lib/carelink/private/evidences}") String directory,
            @Value("${carelink.caregiver.evidence-max-bytes:5242880}") int maxBytes,
            @Value("${carelink.caregiver.evidence-max-pixels:20000000}") long maxPixels) {
        root=Path.of(directory).toAbsolutePath().normalize();
        if(maxBytes<1 || maxBytes>5242880 || maxPixels<1 || maxPixels>20000000) throw new IllegalArgumentException("Evidence limits must stay within 5 MiB and 20 MP.");
        this.maxBytes=maxBytes; this.maxPixels=maxPixels;
    }
    @Override public Prepared prepare(String filename,String declaredType,byte[] source) {
        if(filename==null || filename.isBlank() || filename.length()>255 || filename.matches(".*[/\\\\:].*") || filename.chars().anyMatch(c->c<32 || c==127)) throw invalid("Use a simple image filename without paths.");
        if(source==null || source.length==0 || source.length>maxBytes) throw invalid("Choose a non-empty image within the evidence size limit (at most 5 MiB).");
        String extension=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String format=switch(extension) { case "jpg","jpeg"->"jpeg"; case "png"->"png"; default->throw invalid("Choose a JPEG or PNG image."); };
        String type="image/"+format;
        if(declaredType!=null && !declaredType.equals(type)) throw invalid("Image type and filename do not match.");
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            var decoders=ImageIO.getImageReaders(input);
            if(!decoders.hasNext()) throw invalid("The image cannot be decoded.");
            var reader=decoders.next();
            try {
                reader.setInput(input);
                if(!reader.getFormatName().equalsIgnoreCase(format) && !(format.equals("jpeg") && reader.getFormatName().equalsIgnoreCase("jpg"))) throw invalid("The actual image type does not match.");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0 || height<=0 || (long)width*height>maxPixels) throw invalid("Image exceeds the configured pixel limit (at most 20 MP).");
                var decoded=reader.read(0); var bytes=new ByteArrayOutputStream();
                try(var output=new MemoryCacheImageOutputStream(bytes)) {
                    if(!ImageIO.write(decoded,format,output)) throw invalid("Unsupported image encoding.");
                }
                var stored=bytes.toByteArray();
                if(stored.length>maxBytes) throw invalid("The processed image exceeds the size limit.");
                return new Prepared(filename.strip(),type,stored,hash(source),hash(stored));
            } finally { reader.dispose(); }
        } catch(IOException e) { throw invalid("The image is damaged or unreadable."); }
    }
    @Override public String save(Prepared image) {
        String key=UUID.randomUUID()+(image.contentType().equals("image/png") ? ".png" : ".jpg");
        try {
            ensureRoot();
            try(var channel=FileChannel.open(path(key),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                var bytes=ByteBuffer.wrap(image.bytes()); while(bytes.hasRemaining()) channel.write(bytes); channel.force(true);
            }
            return key;
        } catch(IOException failure) { discard(key); throw unavailable(); }
    }
    @Override public byte[] read(String key) {
        try {
            ensureRoot(); var file=path(key);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>maxBytes) throw unavailable();
            try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
                var result=input.readNBytes(maxBytes+1); if(result.length>maxBytes) throw unavailable(); return result;
            }
        } catch(IOException e) { throw unavailable(); }
    }
    @Override public boolean available(String key) {
        try { ensureRoot(); return Files.isRegularFile(path(key),LinkOption.NOFOLLOW_LINKS); }
        catch(IOException | ResponseStatusException _) { return false; }
    }
    @Override public void discard(String key) {
        try { ensureRoot(); Files.deleteIfExists(path(key)); }
        catch(IOException | ResponseStatusException _) { org.slf4j.LoggerFactory.getLogger(PrivateEvidenceImages.class).warn("Rolled-back evidence cleanup deferred; reconcile private orphan files"); }
    }
    private void ensureRoot() throws IOException { ancestors(); Files.createDirectories(root); ancestors(); }
    private void ancestors() throws IOException {
        for(Path p=root;p!=null;p=p.getParent()) if(Files.exists(p,LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(p) || !p.toRealPath().equals(p))) throw new IOException("Redirected private directory");
    }
    private Path path(String key) {
        if(key==null || !key.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg)")) throw unavailable();
        var file=root.resolve(key).normalize();
        if(!file.getParent().equals(root) || Files.isSymbolicLink(file)) throw unavailable();
        return file;
    }
    public static String hash(byte[] source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException invalid(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"The private evidence file is temporarily unavailable."); }
}
