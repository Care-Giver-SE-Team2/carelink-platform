package sg.nus.carelink.visit.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class PrivateEvidenceImagesTest {
    Path root;
    PrivateEvidenceImages store;
    @BeforeEach void setup() throws Exception {
        var parent=Path.of("../reports/main-sync/tmp"); Files.createDirectories(parent);
        root=Files.createTempDirectory(parent,"evidence-storage-test-"); store=new PrivateEvidenceImages(root.toString(),5242880,20000000);
    }
    @AfterEach void cleanup() throws Exception {
        // This exact root was allocated by this test, below the project-local temp parent.
        try(var paths=Files.walk(root)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
    public static byte[] picture(String format) throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var output=new MemoryCacheImageOutputStream(bytes)) { ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,output); }
        return bytes.toByteArray();
    }
    @Test void pngAndJpegPersistPrivatelyAcrossNewInstances() throws Exception {
        for(String format:List.of("png","jpeg")) {
            var prepared=store.prepare("fake."+format,"image/"+format,picture(format)); var key=store.save(prepared);
            assertThat(key).doesNotContain("fake"); assertThat(store.available(key)).isTrue();
            var reopened=new PrivateEvidenceImages(root.toString(),5242880,20000000);
            assertThat(reopened.read(key)).isEqualTo(prepared.bytes());
            assertThat(PrivateEvidenceImages.hash(reopened.read(key))).isEqualTo(prepared.storedHash());
            store.discard(key); assertThat(store.available(key)).isFalse();
        }
    }
    @Test void rejectsUnsupportedNamesTypesEmptyOversizeAndDamagedImages() throws Exception {
        for(String name:List.of("../a.png","a\\b.png","a:stream.png","a.pdf","a.svg","a\n.png"))
            assertThatThrownBy(()->store.prepare(name,"image/png",new byte[]{1})).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->store.prepare(null,"image/png",new byte[]{1})).hasMessageContaining("filename");
        assertThatThrownBy(()->store.prepare("a.png","image/png",new byte[0])).hasMessageContaining("non-empty");
        assertThatThrownBy(()->store.prepare("a.png","image/png",new byte[5242881])).hasMessageContaining("size limit");
        assertThatThrownBy(()->store.prepare("a.png","image/jpeg",picture("png"))).hasMessageContaining("match");
        assertThatThrownBy(()->store.prepare("a.png","image/png",picture("jpeg"))).hasMessageContaining("actual image");
        assertThatThrownBy(()->store.prepare("a.png","image/png",new byte[]{1})).hasMessageContaining("decoded");
    }
    @Test void checksPixelLimitBeforeDecodingAndValidatesConfiguration() {
        assertThatThrownBy(()->new PrivateEvidenceImages(root.toString(),5242880,3).prepare("a.png","image/png",picture("png"))).hasMessageContaining("pixel limit");
        assertThatThrownBy(()->new PrivateEvidenceImages(root.toString(),5242881,20000000)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingAndTraversalKeysCannotExposeOrRemoveOtherFiles() {
        for(String key:List.of("../secret",UUID.randomUUID()+".png","00000000-0000-0000-0000-000000000000.pdf")) {
            assertThat(store.available(key)).isFalse(); assertThatThrownBy(()->store.read(key)).hasMessageContaining("unavailable"); store.discard(key);
        }
    }
    @Test void directoryLinksCannotRedirectPrivateStorage() throws Exception {
        var target=Files.createDirectory(root.resolve("target")); var link=root.resolve("redirect");
        if(System.getProperty("os.name").startsWith("Windows")) {
            var process=new ProcessBuilder("cmd.exe","/c","mklink","/J",link.toAbsolutePath().toString(),target.toAbsolutePath().toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor()).isZero();
        } else Files.createSymbolicLink(link,target.toAbsolutePath());
        try {
            var redirected=new PrivateEvidenceImages(link.resolve("nested").toString(),5242880,20000000);
            assertThatThrownBy(()->redirected.save(store.prepare("a.png","image/png",picture("png")))).hasMessageContaining("unavailable");
            assertThat(Files.exists(target.resolve("nested"))).isFalse();
        } finally { Files.delete(link); }
    }
}
