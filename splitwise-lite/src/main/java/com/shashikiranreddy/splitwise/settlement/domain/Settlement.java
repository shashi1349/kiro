package com.shashikiranreddy.splitwise.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A recorded payment from one group member to another that reduces the
 * payer's debt and the receiver's credit.
 *
 * <p>Recording a settlement does not delete or modify any expense rows;
 * balance is always recomputed from expenses minus settlements, which keeps
 * the audit trail intact and lets us undo a settlement by deleting its row.
 */
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 200)
    private String note;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt;

    protected Settlement() { /* JPA */ }

    public Settlement(Long groupId, Long fromUserId, Long toUserId,
                      BigDecimal amount, String note) {
        this.groupId = groupId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.note = note;
    }

    @PrePersist
    void prePersist() {
        if (settledAt == null) {
            settledAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getFromUserId() { return fromUserId; }
    public Long getToUserId() { return toUserId; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
    public Instant getSettledAt() { return settledAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Settlement other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
