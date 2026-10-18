package sg.nus.carelink.profile.infrastructure.storage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Runs only in a memory/time-limited child JVM; it never renders or extracts embedded files. */
public final class CredentialPdfCheck {
    private static final Set<String> DISALLOWED = Set.of("JS", "JavaScript", "AA", "OpenAction", "A",
            "EmbeddedFiles", "EF", "XFA", "RichMedia", "Launch", "SubmitForm", "ImportData");
    private CredentialPdfCheck() { }
    public static void main(String[] args) {
        try { verify(new File(args[0])); }
        catch (Exception | StackOverflowError ex) { System.exit(2); }
    }
    static void verify(File file) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(file)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 20)
                throw new IllegalArgumentException("Unsupported PDF");
            Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<COSBase> queue = new ArrayDeque<>();
            queue.add(pdf.getDocumentCatalog().getCOSObject());
            if (pdf.getDocument().getXrefTable().size() > 100_000) throw new IllegalArgumentException("PDF complexity limit");
            pdf.getDocument().getXrefTable().keySet().forEach(key -> queue.add(pdf.getDocument().getObjectFromPool(key)));
            while (!queue.isEmpty()) {
                COSBase value = queue.removeFirst();
                if (!seen.add(value)) continue;
                if (seen.size() > 100_000) throw new IllegalArgumentException("PDF complexity limit");
                if (value instanceof COSObject object) { if (object.getObject() != null) queue.add(object.getObject()); }
                else if (value instanceof COSDictionary dict) {
                    for (var entry : dict.entrySet()) {
                        if (DISALLOWED.contains(entry.getKey().getName())) throw new IllegalArgumentException("Active PDF content");
                        if (entry.getValue() != null) queue.add(entry.getValue());
                    }
                } else if (value instanceof COSArray array) { for (COSBase item : array) if (item != null) queue.add(item); }
            }
        }
    }
}
