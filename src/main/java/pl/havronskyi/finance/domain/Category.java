package pl.havronskyi.finance.domain;

import jakarta.persistence.*;

/**
 * The definition is part of the contract with the model - it goes verbatim into the prompt.
 * Without it the model mixes up Fridge/Restaurants/Delivery and Payments/Apartments,
 * because the category names alone are ambiguous.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, columnDefinition = "text")
    private String definition = "";

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
