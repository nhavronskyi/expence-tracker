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
import pl.havronskyi.finance.repo.ImportBatchRepository;
import pl.havronskyi.finance.repo.RawTransactionRepository;
import pl.havronskyi.finance.repo.ReviewItemRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
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
    private final ExchangeRateService exchangeRates;
    private final ImportJobRegistry jobRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImportService(StatementParser parser, ImportBatchRepository batches,
                         RawTransactionRepository raws, TxnRepository txns,
                         ReviewItemRepository reviews, MerchantNormalizer normalizer,
                         TransferMatcher transferMatcher, RuleEngine ruleEngine,
                         LlmCategorizer llm, FinanceProperties props, ExchangeRateService exchangeRates,
                         ImportJobRegistry jobRegistry) {
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
        this.exchangeRates = exchangeRates;
        this.jobRegistry = jobRegistry;
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Kicks off the import in the background and returns immediately with a job id to poll -
     * the slow part (LLM categorization) can be a real multi-minute chain of HTTP calls, and
     * the caller needs to see progress and be able to cancel it, not just block on one request.
     */
    public String startImport(Long workspaceId, Long accountId, String filename, byte[] content) {
        ImportJob job = jobRegistry.create();
        Thread.ofVirtual().name("import-" + job.getId())
                .start(() -> runImport(job, workspaceId, accountId, filename, content));
        return job.getId();
    }

    /**
     * Runs on a virtual thread, not wrapped in one big transaction - each repository save
     * below is already individually transactional. This is what makes partial progress survive
     * a cancellation (or a mid-import failure): re-uploading the same file afterward is safe,
     * existing rows are skipped via the dedupKey check below, so a partial run is resumable,
     * not corrupting.
     */
    private void runImport(ImportJob job, Long workspaceId, Long accountId, String filename, byte[] content) {
        try {
            String sha = sha256(content);

            ImportBatch batch = new ImportBatch();
            batch.setWorkspaceId(workspaceId);
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
                t.setWorkspaceId(workspaceId);
                t.setRawId(raw.getId());
                t.setAccountId(accountId);
                t.setTxnDate(row.txnDate());
                t.setBookedDate(row.bookedDate());
                t.setAmountMinor(row.amountMinor());
                t.setCurrency(row.currency());
                if (!row.currency().equalsIgnoreCase(props.baseCurrency())) {
                    t.setAmountPlnMinor(exchangeRates.toPlnMinor(row.currency(), row.txnDate(), row.amountMinor()));
                }
                t.setCounterpartyIban(row.counterpartyIban());
                t.setMerchantRaw(row.counterparty());
                t.setMerchantNorm(normalizer.normalize(row.counterparty(), row.description()));
                t.setDescription(row.description());
                t.setDedupKey(dedupKey);

                inserted.add(txns.save(t));
            }

            int transfers = classifyKinds(inserted, workspaceId);
            int byRule = applyRules(inserted, workspaceId);

            job.setPhase(ImportJob.Phase.CATEGORIZING);
            int byLlm = applyLlm(inserted, job, workspaceId);

            transferMatcher.pairLegs(inserted, workspaceId);
            txns.saveAll(inserted);

            int queued = (int) inserted.stream().filter(Txn::isNeedsReview).count();
            batches.save(batch);

            log.info("Import {}: {} wierszy, {} nowych, {} duplikatow, {} do review",
                    filename, rows.size(), inserted.size(), duplicates, queued);

            job.complete(new ImportSummary(batch.getId(), rows.size(), inserted.size(),
                    duplicates, transfers, byRule, byLlm, queued));
        } catch (Exception e) {
            log.error("Import {} nieudany: {}", filename, e.getMessage());
            job.fail(e.getMessage());
        }
    }

    /**
     * Deterministic step. No LLM involved here.
     */
    private int classifyKinds(List<Txn> batch, Long workspaceId) {
        int transfers = 0;
        for (Txn t : batch) {
            if (transferMatcher.markIfOwnIban(t, workspaceId)) {
                transfers++;
            } else if (t.getAmountMinor() > 0) {
                t.setKind(TxnKind.INCOME);
            } else {
                t.setKind(TxnKind.EXPENSE);
            }
        }
        return transfers;
    }

    private int applyRules(List<Txn> batch, Long workspaceId) {
        int hits = 0;
        for (Txn t : batch) {
            if (t.getKind() == TxnKind.INTERNAL_TRANSFER) continue;
            Optional<MerchantRule> rule = ruleEngine.match(t, workspaceId);
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

    private int applyLlm(List<Txn> batch, ImportJob job, Long workspaceId) {
        List<Txn> pending = batch.stream()
                .filter(t -> t.getCategory() == null)
                .filter(t -> t.getKind() == TxnKind.EXPENSE || t.getKind() == TxnKind.INCOME)
                .toList();
        job.setTotal(pending.size());
        if (pending.isEmpty()) return 0;

        Map<Long, Txn> byId = pending.stream().collect(Collectors.toMap(Txn::getId, Function.identity()));
        List<CategorySuggestion> suggestions = llm.classify(pending, workspaceId, job::setProcessed,
                job::isCancelRequested);
        Set<Long> answered = new HashSet<>();
        int accepted = 0;

        for (CategorySuggestion s : suggestions) {
            Txn t = byId.get(s.txnId());
            if (t == null || s.best() == null) continue;
            answered.add(s.txnId());

            Suggestion best = s.best();
            // Income always needs a human's eyes on it at least once - only a learned
            // merchant rule (applyRules, above) can auto-categorize income going forward.
            // Raw LLM confidence alone never silently accepts it, unlike expenses.
            boolean autoAccept = t.getKind() == TxnKind.EXPENSE
                    && best.confidence().compareTo(props.llmConfidenceThreshold()) >= 0;
            if (autoAccept) {
                t.setCategory(best.category());
                t.setCategorySource(CategorySource.LLM);
                t.setConfidence(best.confidence());
                accepted++;
            } else {
                queueForReview(t, s.ranked());
            }
        }

        // Transactions the model didn't answer for (including anything left over after a
        // cancellation) must not silently disappear.
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
        item.setWorkspaceId(t.getWorkspaceId());
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

    /**
     * Re-runs merchant normalization for every already-imported row. Needed whenever
     * MerchantNormalizer's rules improve - e.g. learning to strip order-id-style reference
     * codes - since merchant_norm is computed once at import time and never touched again
     * on its own. Fixes both review-queue grouping and future "remember this merchant" rules.
     */
    @Transactional
    public int renormalizeMerchants(Long workspaceId) {
        int changed = 0;
        for (Txn t : txns.findAllByWorkspaceId(workspaceId)) {
            String updated = normalizer.normalize(t.getMerchantRaw(), t.getDescription());
            if (!Objects.equals(updated, t.getMerchantNorm())) {
                t.setMerchantNorm(updated);
                txns.save(t);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Re-runs IBAN-based transfer detection over every existing EXPENSE/INCOME row. Needed
     * because transfer classification only runs once at import time - a transaction imported
     * before its counterparty account was registered never gets a second chance, and stays
     * miscategorized as a plain expense/income forever unless this runs.
     */
    @Transactional
    public int reclassifyTransfers(Long workspaceId) {
        List<Txn> candidates = txns.findByWorkspaceIdAndKindIn(workspaceId, List.of(TxnKind.EXPENSE, TxnKind.INCOME));
        List<Txn> reclassified = new ArrayList<>();
        for (Txn t : candidates) {
            if (transferMatcher.markIfOwnIban(t, workspaceId)) {
                t.setCategory(null);
                reclassified.add(t);
            }
        }
        if (!reclassified.isEmpty()) {
            transferMatcher.pairLegs(reclassified, workspaceId);
            txns.saveAll(reclassified);
        }
        return reclassified.size();
    }

    /**
     * Re-runs NBP conversion for previously-imported rows that missed it - e.g. because NBP
     * was briefly unreachable during the original import. Same code path as import-time.
     */
    @Transactional
    public int backfillMissingFxAmounts(Long workspaceId) {
        List<Txn> missing = txns.findByWorkspaceIdAndAmountPlnMinorIsNullAndCurrencyNot(workspaceId,
                props.baseCurrency());
        int fixed = 0;
        for (Txn t : missing) {
            Long pln = exchangeRates.toPlnMinor(t.getCurrency(), t.getTxnDate(), t.getAmountMinor());
            if (pln != null) {
                t.setAmountPlnMinor(pln);
                txns.save(t);
                fixed++;
            }
        }
        return fixed;
    }

    /**
     * Wipes transaction data (review items, transactions, raw rows, import batches) so imports
     * can be tested from a clean slate, without losing accounts/categories/learned rules.
     */
    @Transactional
    public void clearTransactionData(Long workspaceId) {
        List<Long> batchIds = batches.findAllByWorkspaceId(workspaceId).stream().map(ImportBatch::getId).toList();
        reviews.deleteAllByWorkspaceId(workspaceId);
        txns.deleteAllByWorkspaceId(workspaceId);
        raws.deleteAllByBatchIdIn(batchIds);
        batches.deleteAllByWorkspaceId(workspaceId);
    }
}
