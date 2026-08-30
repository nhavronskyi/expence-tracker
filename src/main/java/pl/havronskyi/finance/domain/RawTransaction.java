package pl.havronskyi.finance.domain;

import jakarta.persistence.*;

/**
 * The raw file row. Never modified - allows re-parsing everything from scratch.
 */
@Entity
@Table(name = "raw_transaction")
public class RawTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "raw_line", nullable = false, columnDefinition = "text")
    private String rawLine;

    public Long getId() {
        return id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int lineNo) {
        this.lineNo = lineNo;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }
}
