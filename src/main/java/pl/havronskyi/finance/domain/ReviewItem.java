package pl.havronskyi.finance.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "review_item")
public class ReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "txn_id", nullable = false, unique = true)
    private Long txnId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    /**
     * JSON: [{"category":"FRIDGE","confidence":0.62,"reason":"..."}]
     */
    @Column(nullable = false, columnDefinition = "text")
    private String suggestions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewStatus status = ReviewStatus.OPEN;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(String suggestions) {
        this.suggestions = suggestions;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
