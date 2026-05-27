package com.shashikiranreddy.splitwise.settlement.application;

import com.shashikiranreddy.splitwise.settlement.application.DebtSimplifier.Transfer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DebtSimplifier}.
 *
 * <p>Each test verifies two invariants:
 * <ol>
 *   <li>The sum of transfer amounts equals the absolute total credit (= absolute total debt).</li>
 *   <li>The number of transfers is at most {@code N - 1} where {@code N} is
 *       the number of users with a non-zero balance.</li>
 * </ol>
 */
class DebtSimplifierTest {

    private final DebtSimplifier simplifier = new DebtSimplifier();

    private static BigDecimal sumAmounts(List<Transfer> transfers) {
        return transfers.stream().map(Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long nonZero(Map<Long, BigDecimal> balances) {
        return balances.values().stream().filter(b -> b.signum() != 0).count();
    }

    @Test
    void empty_balances_produce_no_transfers() {
        assertThat(simplifier.simplify(Map.of())).isEmpty();
    }

    @Test
    void all_zero_balances_produce_no_transfers() {
        Map<Long, BigDecimal> balances = Map.of(
                1L, BigDecimal.ZERO,
                2L, BigDecimal.ZERO);

        assertThat(simplifier.simplify(balances)).isEmpty();
    }

    @Test
    void one_creditor_and_one_debtor_settle_in_a_single_transfer() {
        // A is owed 100, B owes 100.
        Map<Long, BigDecimal> balances = Map.of(
                1L, new BigDecimal("100.00"),
                2L, new BigDecimal("-100.00"));

        List<Transfer> transfers = simplifier.simplify(balances);

        assertThat(transfers).hasSize(1);
        Transfer t = transfers.get(0);
        assertThat(t.fromUserId()).isEqualTo(2L);
        assertThat(t.toUserId()).isEqualTo(1L);
        assertThat(t.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void classic_three_person_case_settles_in_one_transfer() {
        // A paid 100, B paid 50, total 150 split equally (50 each).
        // Net balances: A = +50, B = 0, C = -50.
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("50.00"));
        balances.put(2L, BigDecimal.ZERO);
        balances.put(3L, new BigDecimal("-50.00"));

        List<Transfer> transfers = simplifier.simplify(balances);

        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).fromUserId()).isEqualTo(3L);
        assertThat(transfers.get(0).toUserId()).isEqualTo(1L);
        assertThat(transfers.get(0).amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void multi_creditor_multi_debtor_case_uses_at_most_n_minus_one_transfers() {
        // A is owed 60, B is owed 40, C owes 50, D owes 50.
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("60.00"));
        balances.put(2L, new BigDecimal("40.00"));
        balances.put(3L, new BigDecimal("-50.00"));
        balances.put(4L, new BigDecimal("-50.00"));

        List<Transfer> transfers = simplifier.simplify(balances);

        // 4 non-zero users -> at most 3 transfers.
        assertThat(transfers.size()).isLessThanOrEqualTo((int) nonZero(balances) - 1);
        // Transfers should fully settle the credit side: total moved = 100.
        assertThat(sumAmounts(transfers)).isEqualByComparingTo("100.00");
        // Every transfer must be from a debtor to a creditor.
        assertThat(transfers).allSatisfy(t -> {
            assertThat(balances.get(t.fromUserId())).isLessThan(BigDecimal.ZERO);
            assertThat(balances.get(t.toUserId())).isGreaterThan(BigDecimal.ZERO);
            assertThat(t.amount()).isGreaterThan(BigDecimal.ZERO);
        });
    }

    @Test
    void cent_level_precision_is_preserved() {
        // 33.34 + 33.33 + 33.33 = 100.00
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("100.00"));
        balances.put(2L, new BigDecimal("-33.34"));
        balances.put(3L, new BigDecimal("-33.33"));
        balances.put(4L, new BigDecimal("-33.33"));

        List<Transfer> transfers = simplifier.simplify(balances);

        assertThat(sumAmounts(transfers)).isEqualByComparingTo("100.00");
        assertThat(transfers.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void zero_balances_are_ignored() {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("100.00"));
        balances.put(2L, BigDecimal.ZERO);
        balances.put(3L, new BigDecimal("-100.00"));
        balances.put(4L, BigDecimal.ZERO);

        List<Transfer> transfers = simplifier.simplify(balances);

        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).fromUserId()).isNotIn(2L, 4L);
        assertThat(transfers.get(0).toUserId()).isNotIn(2L, 4L);
    }
}
