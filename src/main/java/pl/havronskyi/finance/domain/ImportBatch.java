package pl.havronskyi.finance.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    /** Idempotency: the same file uploaded a second time is rejected. */
    @Column(nullable = false, unique = true, length = 64)
    private String sha256;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String format;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public Instant getImportedAt() { return importedAt; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
}
