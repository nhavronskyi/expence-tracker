package pl.havronskyi.finance.ingest;

import org.junit.jupiter.api.Test;
import pl.havronskyi.finance.pipeline.DedupKey;
import pl.havronskyi.finance.pipeline.MerchantNormalizer;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ParsingTest {

    @Test
    void parsesPolishAmountFormats() {
        assertEquals(-123456L, PekaoCsvParser.parseAmountMinor("-1 234,56"));
        assertEquals(-123456L, PekaoCsvParser.parseAmountMinor("-1.234,56"));
        assertEquals(1200L, PekaoCsvParser.parseAmountMinor("12,00"));
        assertEquals(-1250L, PekaoCsvParser.parseAmountMinor("-12,5"));
        assertEquals(500000L, PekaoCsvParser.parseAmountMinor("5000"));
    }

    @Test
    void extractsIbanFromTransferTitle() {
        assertEquals("PL61109010140000071219812874",
                PekaoCsvParser.extractIban("Przelew na rachunek PL61 1090 1014 0000 0712 1981 2874"));
        assertEquals("", PekaoCsvParser.extractIban("BIEDRONKA 1234 WARSZAWA"));
    }

    @Test
    void collapsesStoreNumbersToOneMerchantKey() {
        MerchantNormalizer n = new MerchantNormalizer();
        String a = n.normalize("BIEDRONKA 1234 WARSZAWA", "");
        String b = n.normalize("BIEDRONKA 5678 KRAKOW", "");
        assertEquals(a, b, "Rozne sklepy tej samej sieci musza dac ten sam klucz");
        assertEquals("BIEDRONKA", a);
    }

    @Test
    void collapsesOrderIdsToOneMerchantKey() {
        MerchantNormalizer n = new MerchantNormalizer();
        String a = n.normalize("AMZN QK8RKWV6PPJD5SIEK LUXEMBOURG", "");
        String b = n.normalize("AMZN CR5JH4665 LUXEMBOURG LU", "");
        String c = n.normalize("AMZN 1CGXNLTA3XH2QCMDA LUXEMBOURG", "");
        assertEquals(a, b, "Rozne numery zamowien tego samego sklepu musza dac ten sam klucz");
        assertEquals(a, c);
        assertEquals("AMZN LUXEMBOURG", a);
    }

    @Test
    void identicalSameDayTransactionsGetDistinctKeys() {
        String first = DedupKey.of(1L, LocalDate.of(2026, 7, 4), -1200, "KAWA", 0);
        String second = DedupKey.of(1L, LocalDate.of(2026, 7, 4), -1200, "KAWA", 1);
        assertNotEquals(first, second, "Dwie kawy po 12 zl tego samego dnia to dwie transakcje");
    }
}
