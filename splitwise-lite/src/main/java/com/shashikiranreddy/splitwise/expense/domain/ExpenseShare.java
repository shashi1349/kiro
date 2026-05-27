package com.shashikiranreddy.splitwise.expense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One participant's portion of a single {@link Expense}.
 *
 * <p>The (expense_id, user_id) pair is unique — the same person cannot have
 * two share rows for the same expense.
 */
@Entity
@Table(name = "expense_shares",
        uniqueConstraints = @UniqueConstraint(name = "uq_expense_share_user",
                columnNames = {"expense_id", "user_id"}))
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "share_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal shareAmount;

    protected ExpenseShare() { /* JPA */ }

    public ExpenseShare(Long expenseId, Long userId, BigDecimal shareAmount) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.shareAmount = shareAmount;
    }

    public Long getId() { return id; }
    public Long getExpenseId() { return expenseId; }
    public Long getUserId() { return userId; }
    public BigDecimal getShareAmount() { return shareAmount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseShare other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
