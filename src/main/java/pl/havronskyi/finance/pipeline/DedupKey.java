package pl.havronskyi.finance.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Pekao's export doesn't give a stable operation ID, so we build the key ourselves.
 * seq distinguishes two identical transactions on the same day (two coffees at 12 zl).
 */
public final class DedupKey {

    private DedupKey() {
    }

    public static String of(Long accountId, LocalDate txnDate, long amountMinor,
                            String descriptionRaw, int seq) {
        String payload = accountId + "|" + txnDate + "|" + amountMinor + "|"
                + (descriptionRaw == null ? "" : descriptionRaw.trim()) + "|" + seq;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
