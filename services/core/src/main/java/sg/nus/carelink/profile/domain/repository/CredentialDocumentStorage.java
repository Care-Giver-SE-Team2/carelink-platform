package sg.nus.carelink.profile.domain.repository;

import java.util.Arrays;
import java.util.Objects;

/** Bounded inputs only; implementations keep files outside public/static resources. */
public interface CredentialDocumentStorage {
    record Upload(String fileName, String contentType, byte[] bytes) {
        public Upload { bytes = bytes == null ? null : bytes.clone(); }
        @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Upload value && Objects.equals(fileName, value.fileName)
                    && Objects.equals(contentType, value.contentType) && Arrays.equals(bytes, value.bytes);
        }
        @Override public int hashCode() { return Objects.hash(fileName, contentType, Arrays.hashCode(bytes)); }
        @Override public String toString() {
            return "Upload[fileName=" + fileName + ", contentType=" + contentType + ", bytesHash=" + Arrays.hashCode(bytes) + "]";
        }
    }
    record Prepared(String fileName, String contentType, byte[] bytes, String originalHash) {
        public Prepared { bytes = bytes == null ? null : bytes.clone(); }
        @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Prepared value && Objects.equals(fileName, value.fileName)
                    && Objects.equals(contentType, value.contentType) && Arrays.equals(bytes, value.bytes)
                    && Objects.equals(originalHash, value.originalHash);
        }
        @Override public int hashCode() { return Objects.hash(fileName, contentType, Arrays.hashCode(bytes), originalHash); }
        @Override public String toString() {
            return "Prepared[fileName=" + fileName + ", contentType=" + contentType + ", bytesHash=" + Arrays.hashCode(bytes)
                    + ", originalHash=" + originalHash + "]";
        }
    }
    Prepared prepare(Upload upload);
    String save(Prepared document);
    byte[] read(String storageKey);
    boolean available(String storageKey);
    void discard(String storageKey);
}
