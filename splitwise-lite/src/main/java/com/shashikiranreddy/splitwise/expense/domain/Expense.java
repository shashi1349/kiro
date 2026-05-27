package com.shashikiranreddy.splitwise.expense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single expense paid by one user on behalf of a group.
 *
 * <p>The amount is stored as a decimal with two fractional digits; combined
 * with the {@link ExpenseShare} rows, it forms a complete picture of who
 * paid what and who owes what for this expense.
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "paid_by", nullable = false)
    private Long paidBy;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Expense() { /* JPA */ }

    public Expense(Long groupId, Long paidBy, String description,
                   BigDecimal amount, SplitType splitType) {
        this.groupId = groupId;
        this.paidBy = paidBy;
        this.description = description;
        this.amount = amount;
        this.splitType = splitType;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getPaidBy() { return paidBy; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public SplitType getSplitType() { return splitType; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Expense other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
