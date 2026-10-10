package sg.nus.carelink.visit.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixed fields avoid ambiguous concatenation; raw care text is never persisted here. */
public final class CommandFingerprint {
    private CommandFingerprint() {}
    public static String of(Object... fields) {
        var text = new StringBuilder();
        for (var field : fields) { String value = field == null ? "" : field.toString(); text.append(value.length()).append(':').append(value); }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
