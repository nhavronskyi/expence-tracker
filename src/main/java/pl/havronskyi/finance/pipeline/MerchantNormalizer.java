package pl.havronskyi.finance.pipeline;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The single biggest win in the whole pipeline, and it costs nothing.
 * "BIEDRONKA 1234 WARSZAWA" and "BIEDRONKA 5678 KRAKOW" must produce the same key,
 * otherwise every subsequent Biedronka ends up in the question queue.
 */
@Component
public class MerchantNormalizer {

    private static final List<Pattern> NOISE = List.of(
            Pattern.compile("(?i)\\bplatnosc (kart[aą]|blik|mobilna)\\b"),
            Pattern.compile("(?i)\\btransakcja (kart[aą]|bezgotowkowa)\\b"),
            Pattern.compile("(?i)\\bnr karty\\b.*"),
            Pattern.compile("(?i)\\bkarta \\d{4}\\b"),
            Pattern.compile("(?i)\\bdata transakcji\\b.*"),
            Pattern.compile("(?i)\\b(pol|pl|warszawa|krakow|gdansk|wroclaw|poznan|lodz)\\b"),
            Pattern.compile("\\*+"),
            Pattern.compile("\\b\\d{3,}\\b")          // store numbers, terminal IDs, references
    );

    private static final Pattern MULTISPACE = Pattern.compile("\\s{2,}");

    public String normalize(String merchantRaw, String description) {
        String base = (merchantRaw == null || merchantRaw.isBlank()) ? description : merchantRaw;
        if (base == null) return "";

        String s = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();

        for (Pattern p : NOISE) {
            s = p.matcher(s).replaceAll(" ");
        }
        s = s.replaceAll("[^A-Z0-9 .&-]", " ");
        s = MULTISPACE.matcher(s).replaceAll(" ").trim();

        // Very long transfer descriptions get truncated - they aren't a stable key anyway.
        return s.length() > 64 ? s.substring(0, 64).trim() : s;
    }
}
