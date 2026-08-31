package pl.havronskyi.finance.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized transaction. Amounts in cents (long), to avoid rounding errors.
 * Sign: negative = expense, positive = income.
 */
@Entity
@Table(name = "txn")
public class Txn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_id", nullable = false)
    private Long rawId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "booked_date", nullable = false)
    private LocalDate bookedDate;

    /**
     * The actual transaction date - the month is counted by this, not by the booking date.
     */
    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_pln_minor")
    private Long amountPlnMinor;

    @Column(name = "counterparty_iban")
    private String counterpartyIban;

    @Column(name = "merchant_raw", length = 512)
    private String merchantRaw;

    @Column(name = "merchant_norm")
    private String merchantNorm;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TxnKind kind = TxnKind.UNKNOWN;

    @Column(length = 32)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", length = 16)
    private CategorySource categorySource;

    private BigDecimal confidence;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "transfer_group")
    private UUID transferGroup;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 64)
    private String dedupKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isExpense() {
        return kind == TxnKind.EXPENSE;
    }

    public Long getId() {
        return id;
    }

    public Long getRawId() {
        return rawId;
    }

    public void setRawId(Long rawId) {
        this.rawId = rawId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public LocalDate getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate(LocalDate bookedDate) {
        this.bookedDate = bookedDate;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getAmountPlnMinor() {
        return amountPlnMinor;
    }

    public void setAmountPlnMinor(Long amountPlnMinor) {
        this.amountPlnMinor = amountPlnMinor;
    }

    public String getCounterpartyIban() {
        return counterpartyIban;
    }

    public void setCounterpartyIban(String counterpartyIban) {
        this.counterpartyIban = counterpartyIban;
    }

    public String getMerchantRaw() {
        return merchantRaw;
    }

    public void setMerchantRaw(String merchantRaw) {
        this.merchantRaw = merchantRaw;
    }

    public String getMerchantNorm() {
        return merchantNorm;
    }

    public void setMerchantNorm(String merchantNorm) {
        this.merchantNorm = merchantNorm;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TxnKind getKind() {
        return kind;
    }

    public void setKind(TxnKind kind) {
        this.kind = kind;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public void setCategorySource(CategorySource categorySource) {
        this.categorySource = categorySource;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public UUID getTransferGroup() {
        return transferGroup;
    }

    public void setTransferGroup(UUID transferGroup) {
        this.transferGroup = transferGroup;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
