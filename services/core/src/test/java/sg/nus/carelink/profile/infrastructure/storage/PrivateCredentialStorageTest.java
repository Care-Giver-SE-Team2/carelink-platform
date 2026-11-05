package sg.nus.carelink.profile.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.cos.COSName;
import static org.assertj.core.api.Assertions.*;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage.Upload;

class PrivateCredentialStorageTest {
    Path root;
    PrivateCredentialStorage store;
    @BeforeEach void setup() throws Exception {
        Path parent = Path.of("target/main-sync/tmp"); Files.createDirectories(parent);
        root = Files.createTempDirectory(parent,"credential-storage-test-");
        store = new PrivateCredentialStorage(root.toString(),parent.toString());
    }
    @AfterEach void cleanup() throws Exception {
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
    static byte[] picture(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.setUseCache(false);
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,output);
        return output.toByteArray();
    }
    @Test void imagesPersistPrivatelyAndSurviveNewStorageInstance() throws Exception {
        for (String extension : List.of("png","jpg")) {
            var prepared = store.prepare(new Upload("demo." + extension,extension.equals("png") ? "image/png" : "image/jpeg",picture(extension)));
            String key = store.save(prepared);
            assertThat(key).doesNotContain("demo"); assertThat(store.available(key)).isTrue();
            assertThat(new PrivateCredentialStorage(root.toString(),root.toString()).read(key)).isEqualTo(prepared.bytes());
            store.discard(key); assertThat(store.available(key)).isFalse();
        }
    }
    @Test void simplePdfIsCheckedInBoundedWorker() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PDDocument()) { pdf.addPage(new PDPage()); pdf.save(output); }
        var prepared = store.prepare(new Upload("demo.pdf","application/pdf",output.toByteArray()));
        assertThat(prepared.bytes()).isEqualTo(output.toByteArray());
        String key = store.save(prepared); assertThat(store.read(key)).isEqualTo(output.toByteArray());
    }
    @Test void activeAndExcessPagePdfsAreRejected() throws Exception {
        for (boolean active : List.of(true,false)) {
            var output = new ByteArrayOutputStream();
            try (var pdf = new PDDocument()) {
                for (int i=0;i<(active ? 1 : 21);i++) pdf.addPage(new PDPage());
                if (active) pdf.getDocumentCatalog().getCOSObject().setString(COSName.getPDFName("JS"),"demo");
                pdf.save(output);
            }
            assertThatThrownBy(() -> store.prepare(new Upload("demo.pdf","application/pdf",output.toByteArray())))
                    .hasMessageContaining("PDF");
        }
    }
    @Test void namesTypesEmptyOversizeAndDamagedDocumentsAreRejected() throws Exception {
        for (String name : List.of("../a.png","a\\b.png","a:stream.png","a.svg","a\n.png"))
            assertThatThrownBy(() -> store.prepare(new Upload(name,"image/png",new byte[]{1}))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> store.prepare(new Upload(null,"image/png",new byte[]{1}))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> store.prepare(new Upload("a.png","image/png",new byte[0]))).hasMessageContaining("non-empty");
        assertThatThrownBy(() -> store.prepare(new Upload("a.png","image/png",new byte[5*1024*1024+1]))).hasMessageContaining("5 MiB");
        assertThatThrownBy(() -> store.prepare(new Upload("a.png","image/jpeg",picture("png")))).hasMessageContaining("type");
        assertThatThrownBy(() -> store.prepare(new Upload("a.png","image/png",picture("jpg")))).hasMessageContaining("actual image");
        assertThatThrownBy(() -> store.prepare(new Upload("a.png","image/png",new byte[]{1}))).hasMessageContaining("decoded");
        assertThatThrownBy(() -> store.prepare(new Upload("a.pdf","application/pdf",new byte[]{1}))).hasMessageContaining("PDF");
    }
    @Test void arbitraryPathsCannotBeReadOrRemoved() {
        assertThat(store.available("../../secret")).isFalse();
        assertThatThrownBy(() -> store.read("../../secret")).hasMessageContaining("unavailable");
        store.discard("../../secret");
    }

    @Test void missingDocumentsAreUnavailableWithoutAcceptingArbitraryKeys() {
        String key=UUID.randomUUID()+".png";
        assertThat(store.available(key)).isFalse();
        assertThatThrownBy(() -> store.read(key)).hasMessageContaining("unavailable");
        store.discard(key);
    }

    @Test void symlinkOrJunctionDirectoryCannotRedirectStorage() throws Exception {
        Path target=Files.createDirectory(root.resolve("target")); Path link=root.resolve("redirect");
        if (System.getProperty("os.name").startsWith("Windows")) {
            // Both exact targets are newly created below this test's project-local temporary root.
            var process=new ProcessBuilder("cmd.exe","/c","mklink","/J",link.toAbsolutePath().toString(),target.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            assertThat(process.waitFor()).isZero();
        } else Files.createSymbolicLink(link,target.toAbsolutePath());
        try {
            var redirected=new PrivateCredentialStorage(link.resolve("nested").toString(),root.toString());
            assertThatThrownBy(() -> redirected.save(store.prepare(new Upload("demo.png","image/png",picture("png")))))
                    .hasMessageContaining("unavailable");
            assertThat(Files.exists(target.resolve("nested"))).isFalse();
        } finally { Files.delete(link); }
    }
}
