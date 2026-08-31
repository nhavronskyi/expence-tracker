package pl.havronskyi.finance.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * The effect of every answer in the review queue. Without this the system
 * would ask about Biedronka every month and the project would be pointless.
 */
@Entity
@Table(name = "merchant_rule")
public class MerchantRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 16)
    private MatchType matchType;

    @Column(nullable = false)
    private String pattern;

    @Column(nullable = false, length = 32)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TxnKind kind;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public TxnKind getKind() {
        return kind;
    }

    public void setKind(TxnKind kind) {
        this.kind = kind;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }
}
