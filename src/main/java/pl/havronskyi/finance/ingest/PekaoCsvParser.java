package pl.havronskyi.finance.ingest;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PekaoCsvParser implements StatementParser {

    private static final Logger log = LoggerFactory.getLogger(PekaoCsvParser.class);
    private static final Pattern IBAN = Pattern.compile("([A-Z]{2}\\d{2}[A-Z0-9]{11,30})");

    private final FinanceProperties props;

    public PekaoCsvParser(FinanceProperties props) {
        this.props = props;
    }

    /**
     * "-1 234,56" -> -123456. No double, no BigDecimal.doubleValue().
     */
    static long parseAmountMinor(String raw) {
        String s = raw.replace(" ", "").replace(" ", "").trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Pusta kwota");
        boolean negative = s.startsWith("-");
        s = s.replace("-", "").replace("+", "");
        // The Polish export uses a comma; a dot is sometimes a thousands separator.
        if (s.contains(",")) {
            s = s.replace(".", "").replace(',', '.');
        }
        String[] parts = s.split("\\.");
        long units = Long.parseLong(parts[0]);
        long cents = 0;
        if (parts.length > 1) {
            String frac = (parts[1] + "00").substring(0, 2);
            cents = Long.parseLong(frac);
        }
        long total = units * 100 + cents;
        return negative ? -total : total;
    }

    static String extractIban(String text) {
        if (text == null) return "";
        Matcher m = IBAN.matcher(text.replace(" ", ""));
        return m.find() ? m.group(1) : "";
    }

    /**
     * Pekao exports account numbers Excel-escaped with a leading apostrophe
     * and no PL prefix, e.g. 'wxyz...426 - strip that so it exact-matches account.iban.
     */
    static String stripAccountNumber(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.startsWith("'") ? t.substring(1) : t;
    }

    private static Map<String, Integer> indexHeader(String[] header) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            idx.put(normalizeHeader(header[i]), i);
        }
        return idx;
    }

    private static String normalizeHeader(String h) {
        return h == null ? "" : h.replace("﻿", "").trim().toLowerCase();
    }

    private static String value(String[] line, Map<String, Integer> idx, String columnName) {
        if (columnName == null) return "";
        Integer i = idx.get(normalizeHeader(columnName));
        if (i == null || i >= line.length || line[i] == null) return "";
        return line[i];
    }

    private static boolean isBlank(String[] line) {
        for (String c : line) {
            if (c != null && !c.isBlank()) return false;
        }
        return true;
    }

    @Override
    public String format() {
        return "PEKAO_CSV";
    }

    @Override
    public List<ParsedRow> parse(InputStream in) {
        var cfg = props.pekao();
        var fmt = DateTimeFormatter.ofPattern(cfg.dateFormat());
        var rows = new ArrayList<ParsedRow>();

        try (Reader reader = new InputStreamReader(in, Charset.forName(cfg.charset()));
             CSVReader csv = new CSVReaderBuilder(reader)
                     .withCSVParser(new CSVParserBuilder()
                             .withSeparator(cfg.delimiter().charAt(0))
                             .build())
                     .build()) {

            String[] header = csv.readNext();
            if (header == null) {
                throw new IllegalArgumentException("Pusty plik CSV");
            }
            Map<String, Integer> idx = indexHeader(header);

            String[] line;
            int lineNo = 1;
            while ((line = csv.readNext()) != null) {
                lineNo++;
                if (isBlank(line)) continue;
                try {
                    rows.add(toRow(line, lineNo, idx, cfg, fmt));
                } catch (RuntimeException e) {
                    // We don't abort the whole import over one broken row -
                    // but we don't silently swallow the error either.
                    log.warn("Wiersz {} pominiety: {}", lineNo, e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Nie udalo sie sparsowac pliku Pekao CSV", e);
        }
        return rows;
    }

    private ParsedRow toRow(String[] line, int lineNo, Map<String, Integer> idx,
                            FinanceProperties.Pekao cfg, DateTimeFormatter fmt) {

        String txnDateRaw = value(line, idx, cfg.columns().get("txn-date"));
        String bookedRaw = value(line, idx, cfg.columns().get("booked-date"));
        String amountRaw = value(line, idx, cfg.columns().get("amount"));
        String currency = value(line, idx, cfg.columns().get("currency"));
        String party = value(line, idx, cfg.columns().get("counterparty"));
        String sourceIban = stripAccountNumber(value(line, idx, cfg.columns().get("counterparty-iban-source")));
        String destIban = stripAccountNumber(value(line, idx, cfg.columns().get("counterparty-iban-dest")));
        String desc = value(line, idx, cfg.columns().get("description"));

        LocalDate txnDate = LocalDate.parse(txnDateRaw, fmt);
        LocalDate booked = bookedRaw.isBlank() ? txnDate : LocalDate.parse(bookedRaw, fmt);
        long amountMinor = parseAmountMinor(amountRaw);

        // Negative (debit): money left FROM the source account, so the destination is
        // the counterparty. Positive (credit): the reverse.
        String partyIban = amountMinor < 0 ? destIban : sourceIban;
        if (partyIban.isBlank()) {
            partyIban = extractIban(desc);
        }

        return new ParsedRow(
                lineNo,
                String.join(cfg.delimiter(), line),
                txnDate,
                booked,
                amountMinor,
                currency.isBlank() ? "PLN" : currency.trim().toUpperCase(),
                party.trim(),
                partyIban,
                desc.trim()
        );
    }
}
