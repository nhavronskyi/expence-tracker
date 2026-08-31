package pl.havronskyi.finance.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A folder-like container: its own bank accounts, categories, merchant rules and
 * transactions. The LLM categorizer and rule engine only ever see data from one
 * workspace at a time.
 */
@Entity
@Table(name = "workspace")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
