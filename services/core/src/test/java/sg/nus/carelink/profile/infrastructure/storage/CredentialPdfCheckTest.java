package sg.nus.carelink.profile.infrastructure.storage;

import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** The same validation called by the child JVM, exercised directly for branch evidence. */
class CredentialPdfCheckTest {
    Path root;
    @BeforeEach void setup() throws Exception {
        Path parent = Path.of("../reports/main-sync/tmp"); Files.createDirectories(parent);
        root = Files.createTempDirectory(parent, "credential-pdf-check-");
    }
    @AfterEach void cleanup() throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    Path save(PDDocument pdf) throws Exception {
        Path file = root.resolve(UUID.randomUUID() + ".pdf"); pdf.save(file.toFile()); return file;
    }
    @Test void simplePdfWithOrdinaryArrayDictionaryAndMetadataIsAccepted() throws Exception {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage()); pdf.getDocumentInformation().setTitle("Fictional certificate");
            COSArray values = new COSArray(); values.add(COSInteger.ONE); values.add(new COSString("ordinary"));
            COSDictionary detail = new COSDictionary(); detail.setItem(COSName.getPDFName("Data"), values);
            pdf.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Demo"), detail);
            Path file = save(pdf);
            assertThatCode(() -> CredentialPdfCheck.verify(file.toFile())).doesNotThrowAnyException();
            assertThatCode(() -> CredentialPdfCheck.main(new String[]{file.toString()})).doesNotThrowAnyException();
        }
    }
    @Test void absentPagesAndExcessPagesAreRejected() throws Exception {
        for (int pages : List.of(0, 21)) {
            try (var pdf = new PDDocument()) {
                for (int i=0; i<pages; i++) pdf.addPage(new PDPage());
                var file = save(pdf).toFile();
                assertThatThrownBy(() -> CredentialPdfCheck.verify(file)).hasMessageContaining("Unsupported PDF");
            }
        }
    }
    @Test void encryptedPdfIsRejectedEvenWhenEmptyUserPasswordAllowsOpening() throws Exception {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            var protection = new StandardProtectionPolicy("fictional-owner", "", new AccessPermission());
            protection.setEncryptionKeyLength(128); pdf.protect(protection);
            var file = save(pdf).toFile();
            assertThatThrownBy(() -> CredentialPdfCheck.verify(file)).hasMessageContaining("Unsupported PDF");
        }
    }
    @Test void activeKeysNestedInArraysAndAttachmentsAreRejected() throws Exception {
        for (String key : List.of("JS", "AA", "OpenAction", "EmbeddedFiles", "EF", "XFA", "Launch", "A")) {
            try (var pdf = new PDDocument()) {
                pdf.addPage(new PDPage());
                COSDictionary prohibited = new COSDictionary(); prohibited.setString(COSName.getPDFName(key), "fictional");
                COSArray array = new COSArray(); array.add(prohibited);
                pdf.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Nested"), array);
                var file = save(pdf).toFile();
                assertThatThrownBy(() -> CredentialPdfCheck.verify(file)).hasMessageContaining("Active PDF content");
            }
        }
    }
    @Test void malformedPdfAndMissingFileFailClosed() throws Exception {
        Path broken = root.resolve("broken.pdf"); Files.write(broken, new byte[]{1,2,3});
        var malformed = broken.toFile(); var missing = root.resolve("missing.pdf").toFile();
        assertThatThrownBy(() -> CredentialPdfCheck.verify(malformed)).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> CredentialPdfCheck.verify(missing)).isInstanceOf(java.io.IOException.class);
    }
}
