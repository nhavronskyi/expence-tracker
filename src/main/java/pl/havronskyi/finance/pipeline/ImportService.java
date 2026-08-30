package pl.havronskyi.finance.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.havronskyi.finance.domain.*;
import pl.havronskyi.finance.ingest.FinanceProperties;
import pl.havronskyi.finance.ingest.ParsedRow;
import pl.havronskyi.finance.ingest.StatementParser;
import pl.havronskyi.finance.llm.CategorySuggestion;
import pl.havronskyi.finance.llm.LlmCategorizer;
import pl.havronskyi.finance.llm.Suggestion;
import pl.havronskyi.finance.repo.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final StatementParser parser;
    private final ImportBatchRepository batches;
    private final RawTransactionRepository raws;
    private final TxnRepository txns;
    private final ReviewItemRepository reviews;
    private final MerchantNormalizer normalizer;
    private final TransferMatcher transferMatcher;
    private final RuleEngine ruleEngine;
    private final LlmCategorizer llm;
    private final FinanceProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImportService(StatementParser parser, ImportBatchRepository batches,
                         RawTransactionRepository raws, TxnRepository txns,
                         ReviewItemRepository reviews, MerchantNormalizer normalizer,
                         TransferMatcher transferMatcher, RuleEngine ruleEngine,
                         LlmCategorizer llm, FinanceProperties props) {
        this.parser = parser;
        this.batches = batches;
        this.raws = raws;
        this.txns = txns;
        this.reviews = reviews;
        this.normalizer = normalizer;
        this.transferMatcher = transferMatcher;
        this.ruleEngine = ruleEngine;
        this.llm = llm;
        this.props = props;
    }

    @Transactional
    public ImportSummary importFile(Long accountId, String filename, byte[] content) {
        String sha = sha256(content);
        if (batches.findBySha256(sha).isPresent()) {
            throw new IllegalArgumentException("Ten plik zostal juz zaimportowany (sha256=" + sha.substring(0, 12) + ")");
        }

        ImportBatch batch = new ImportBatch();
        batch.setFilename(filename);
        batch.setSha256(sha);
        batch.setAccountId(accountId);
        batch.setFormat(parser.format());
        batch = batches.save(batch);

        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(content));
        batch.setRowCount(rows.size());

        // seq distinguishes identical transactions on the same day
        Map<String, Integer> seqCounter = new HashMap<>();
        List<Txn> inserted = new ArrayList<>();
        int duplicates = 0;

        for (ParsedRow row : rows) {
            RawTransaction raw = new RawTransaction();
            raw.setBatchId(batch.getId());
            raw.setLineNo(row.lineNo());
            raw.setRawLine(row.rawLine());
            raw = raws.save(raw);

            String seqBase = accountId + "|" + row.txnDate() + "|" + row.amountMinor();
            int seq = seqCounter.merge(seqBase, 1, Integer::sum) - 1;
            String dedupKey = DedupKey.of(accountId, row.txnDate(), row.amountMinor(), row.description(), seq);

            if (txns.existsByDedupKey(dedupKey)) {
                duplicates++;
                continue;
            }

            Txn t = new Txn();
            t.setRawId(raw.getId());
            t.setAccountId(accountId);
            t.setTxnDate(row.txnDate());
            t.setBookedDate(row.bookedDate());
            t.setAmountMinor(row.amountMinor());
            t.setCurrency(row.currency());
            t.setCounterpartyIban(row.counterpartyIban());
            t.setMerchantRaw(row.counterparty());
            t.setMerchantNorm(normalizer.normalize(row.counterparty(), row.description()));
            t.setDescription(row.description());
            t.setDedupKey(dedupKey);

            inserted.add(txns.save(t));
        }

        int transfers = classifyKinds(inserted);
        int byRule = applyRules(inserted);
        int byLlm = applyLlm(inserted);
        transferMatcher.pairLegs(inserted);
        txns.saveAll(inserted);

        int queued = (int) inserted.stream().filter(Txn::isNeedsReview).count();
        batches.save(batch);

        log.info("Import {}: {} wierszy, {} nowych, {} duplikatow, {} do review",
                filename, rows.size(), inserted.size(), duplicates, queued);

        return new ImportSummary(batch.getId(), rows.size(), inserted.size(),
                duplicates, transfers, byRule, byLlm, queued);
    }

    /** Deterministic step. No LLM involved here. */
    private int classifyKinds(List<Txn> batch) {
        int transfers = 0;
        for (Txn t : batch) {
            if (transferMatcher.markIfOwnIban(t)) {
                transfers++;
            } else if (t.getAmountMinor() > 0) {
                t.setKind(TxnKind.INCOME);
            } else {
                t.setKind(TxnKind.EXPENSE);
            }
        }
        return transfers;
    }

    private int applyRules(List<Txn> batch) {
        int hits = 0;
        for (Txn t : batch) {
            if (t.getKind() == TxnKind.INTERNAL_TRANSFER) continue;
            Optional<MerchantRule> rule = ruleEngine.match(t);
            if (rule.isPresent()) {
                t.setCategory(rule.get().getCategory());
                t.setKind(rule.get().getKind());
                t.setCategorySource(CategorySource.RULE);
                t.setConfidence(BigDecimal.ONE);
                hits++;
            }
        }
        return hits;
    }

    private int applyLlm(List<Txn> batch) {
        List<Txn> pending = batch.stream()
                .filter(t -> t.getCategory() == null)
                .filter(t -> t.getKind() == TxnKind.EXPENSE)
                .toList();
        if (pending.isEmpty()) return 0;

        Map<Long, Txn> byId = pending.stream().collect(Collectors.toMap(Txn::getId, Function.identity()));
        List<CategorySuggestion> suggestions = llm.classify(pending);
        Set<Long> answered = new HashSet<>();
        int accepted = 0;

        for (CategorySuggestion s : suggestions) {
            Txn t = byId.get(s.txnId());
            if (t == null || s.best() == null) continue;
            answered.add(s.txnId());

            Suggestion best = s.best();
            if (best.confidence().compareTo(props.llmConfidenceThreshold()) >= 0) {
                t.setCategory(best.category());
                t.setCategorySource(CategorySource.LLM);
                t.setConfidence(best.confidence());
                accepted++;
            } else {
                queueForReview(t, s.ranked());
            }
        }

        // Transactions the model didn't answer for must not silently disappear.
        for (Txn t : pending) {
            if (!answered.contains(t.getId())) {
                queueForReview(t, List.of());
            }
        }
        return accepted;
    }

    private void queueForReview(Txn t, List<Suggestion> ranked) {
        t.setNeedsReview(true);
        ReviewItem item = new ReviewItem();
        item.setTxnId(t.getId());
        item.setQuestion("%s, %s %s, %s".formatted(
                t.getMerchantNorm() == null || t.getMerchantNorm().isBlank() ? t.getDescription() : t.getMerchantNorm(),
                new BigDecimal(t.getAmountMinor()).movePointLeft(2).toPlainString(),
                t.getCurrency(),
                t.getTxnDate()));
        try {
            item.setSuggestions(mapper.writeValueAsString(ranked));
        } catch (Exception e) {
            item.setSuggestions("[]");
        }
        reviews.save(item);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
