package com.shashikiranreddy.splitwise.expense.application;

import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.BadRequestException;
import com.shashikiranreddy.splitwise.expense.application.SplitCalculator.Input;
import com.shashikiranreddy.splitwise.expense.application.SplitCalculator.Share;
import com.shashikiranreddy.splitwise.expense.domain.SplitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SplitCalculator}. The calculator is a pure function so
 * no mocks are required.
 *
 * <p>Two invariants are checked across every split type:
 * <ol>
 *   <li>Sum of returned shares equals the input total to the cent.</li>
 *   <li>Every input userId appears exactly once in the output.</li>
 * </ol>
 */
class SplitCalculatorTest {

    private final SplitCalculator calculator = new SplitCalculator();

    private static Input p(long userId, String value) {
        return new Input(userId, value == null ? null : new BigDecimal(value));
    }

    private static BigDecimal sum(List<Share> shares) {
        return shares.stream().map(Share::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Nested
    @DisplayName("EQUAL split")
    class Equal {

        @Test
        void splits_an_evenly_divisible_amount_into_equal_shares() {
            List<Share> shares = calculator.split(new BigDecimal("30.00"), SplitType.EQUAL,
                    List.of(p(1, null), p(2, null), p(3, null)));

            assertThat(shares).extracting(Share::amount)
                    .allMatch(a -> a.compareTo(new BigDecimal("10.00")) == 0);
            assertThat(sum(shares)).isEqualByComparingTo("30.00");
        }

        @Test
        void distributes_cent_remainder_to_the_first_few_participants() {
            // 100.00 / 3 = 33.33 base, 1 cent remainder goes to the first participant only.
            List<Share> shares = calculator.split(new BigDecimal("100.00"), SplitType.EQUAL,
                    List.of(p(1, null), p(2, null), p(3, null)));

            assertThat(shares.get(0).amount()).isEqualByComparingTo("33.34");
            assertThat(shares.get(1).amount()).isEqualByComparingTo("33.33");
            assertThat(shares.get(2).amount()).isEqualByComparingTo("33.33");
            assertThat(sum(shares)).isEqualByComparingTo("100.00");
        }

        @Test
        void single_participant_pays_the_whole_amount() {
            List<Share> shares = calculator.split(new BigDecimal("42.50"), SplitType.EQUAL,
                    List.of(p(7, null)));

            assertThat(shares).hasSize(1);
            assertThat(shares.get(0).amount()).isEqualByComparingTo("42.50");
        }
    }

    @Nested
    @DisplayName("EXACT split")
    class Exact {

        @Test
        void accepts_amounts_that_sum_to_total() {
            List<Share> shares = calculator.split(new BigDecimal("100.00"), SplitType.EXACT,
                    List.of(p(1, "30.00"), p(2, "70.00")));

            assertThat(shares).extracting(Share::amount)
                    .containsExactly(new BigDecimal("30.00"), new BigDecimal("70.00"));
        }

        @Test
        void rejects_amounts_that_do_not_sum_to_total() {
            assertThatThrownBy(() ->
                    calculator.split(new BigDecimal("100.00"), SplitType.EXACT,
                            List.of(p(1, "30.00"), p(2, "60.00"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Exact shares sum to");
        }

        @Test
        void rejects_negative_amounts() {
            assertThatThrownBy(() ->
                    calculator.split(new BigDecimal("100.00"), SplitType.EXACT,
                            List.of(p(1, "-10.00"), p(2, "110.00"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("non-negative");
        }
    }

    @Nested
    @DisplayName("PERCENT split")
    class Percent {

        @Test
        void splits_evenly_when_percentages_match() {
            List<Share> shares = calculator.split(new BigDecimal("200.00"), SplitType.PERCENT,
                    List.of(p(1, "50"), p(2, "50")));

            assertThat(shares.get(0).amount()).isEqualByComparingTo("100.00");
            assertThat(shares.get(1).amount()).isEqualByComparingTo("100.00");
        }

        @Test
        void balances_rounding_so_total_matches_to_the_cent() {
            // 10.00 split 33.33 / 33.33 / 33.34 floors to 333/333/333 cents = 999;
            // remainder 1 cent goes to the first participant.
            List<Share> shares = calculator.split(new BigDecimal("10.00"), SplitType.PERCENT,
                    List.of(p(1, "33.33"), p(2, "33.33"), p(3, "33.34")));

            assertThat(sum(shares)).isEqualByComparingTo("10.00");
            // No participant should be off by more than a cent from the ideal share.
            assertThat(shares).extracting(Share::amount)
                    .allMatch(a -> a.compareTo(new BigDecimal("3.33")) >= 0
                                && a.compareTo(new BigDecimal("3.34")) <= 0);
        }

        @Test
        void rejects_percentages_that_do_not_sum_to_100() {
            assertThatThrownBy(() ->
                    calculator.split(new BigDecimal("100.00"), SplitType.PERCENT,
                            List.of(p(1, "50"), p(2, "30"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must sum to 100");
        }

        @Test
        void rejects_percentages_outside_zero_to_one_hundred() {
            assertThatThrownBy(() ->
                    calculator.split(new BigDecimal("100.00"), SplitType.PERCENT,
                            List.of(p(1, "150"), p(2, "-50"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("between 0 and 100");
        }
    }

    @Nested
    @DisplayName("Common validation")
    class Common {

        @Test
        void rejects_non_positive_total() {
            assertThatThrownBy(() ->
                    calculator.split(BigDecimal.ZERO, SplitType.EQUAL, List.of(p(1, null))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void rejects_empty_participant_list() {
            assertThatThrownBy(() ->
                    calculator.split(new BigDecimal("10.00"), SplitType.EQUAL, List.of()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("At least one participant");
        }

        @Test
        void rejects_duplicate_user_ids() {
            assertThatThrownBy(() ->
                    calculator.split(new BigDecimal("10.00"), SplitType.EQUAL,
                            List.of(p(1, null), p(1, null))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Duplicate userId");
        }
    }
}
