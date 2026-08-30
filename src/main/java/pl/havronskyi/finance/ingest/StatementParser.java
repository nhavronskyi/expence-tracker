package pl.havronskyi.finance.ingest;

import java.io.InputStream;
import java.util.List;

/** Stage 1: Pekao CSV only. Enable Banking / Monobank will come in as further implementations. */
public interface StatementParser {

    String format();

    List<ParsedRow> parse(InputStream in);
}
