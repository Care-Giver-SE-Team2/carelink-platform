package sg.nus.carelink.visit.domain.repository;

import java.util.Arrays;
import java.util.Objects;

public interface EvidenceImageStorage {
    record Prepared(String fileName,String contentType,byte[] bytes,String originalHash,String storedHash) {
        public Prepared { bytes=bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Prepared value && Objects.equals(fileName, value.fileName)
                    && Objects.equals(contentType, value.contentType) && Arrays.equals(bytes, value.bytes)
                    && Objects.equals(originalHash, value.originalHash) && Objects.equals(storedHash, value.storedHash);
        }
        @Override public int hashCode() { return Objects.hash(fileName, contentType, Arrays.hashCode(bytes), originalHash, storedHash); }
        @Override public String toString() { return "Prepared evidence image"; }
    }
    Prepared prepare(String filename,String declaredType,byte[] source);
    String save(Prepared image);
    byte[] read(String key);
    boolean available(String key);
    void discard(String key);
}
