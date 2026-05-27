package com.shashikiranreddy.splitwise.balance.application;

import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ForbiddenException;
import com.shashikiranreddy.splitwise.expense.domain.Expense;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseRepository;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseShare;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseShareRepository;
import com.shashikiranreddy.splitwise.expense.domain.SplitType;
import com.shashikiranreddy.splitwise.group.domain.GroupMember;
import com.shashikiranreddy.splitwise.group.domain.GroupMemberRepository;
import com.shashikiranreddy.splitwise.settlement.domain.Settlement;
import com.shashikiranreddy.splitwise.settlement.domain.SettlementRepository;
import com.shashikiranreddy.splitwise.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BalanceService}. All repositories are mocked so the
 * test stays focused on the aggregation logic.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final long GROUP_ID = 1L;

    @Mock GroupMemberRepository memberRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock ExpenseShareRepository shareRepository;
    @Mock SettlementRepository settlementRepository;
    @Mock UserRepository userRepository;

    @InjectMocks BalanceService balanceService;

    @BeforeEach
    void wireGroupMembers() {
        // Three members in the test group: 1, 2, 3.
        lenient().when(memberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(
                member(GROUP_ID, 1L), member(GROUP_ID, 2L), member(GROUP_ID, 3L)));
    }

    @Test
    void single_expense_credits_payer_and_debits_each_participant() {
        // User 1 paid 100; equal split among {1,2,3}.
        Expense e = expense(10L, 1L, "100.00");
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(GROUP_ID)).thenReturn(List.of(e));
        when(shareRepository.findByExpenseIdIn(List.of(10L))).thenReturn(List.of(
                share(10L, 1L, "33.34"),
                share(10L, 2L, "33.33"),
                share(10L, 3L, "33.33")));
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        Map<Long, BigDecimal> balances = balanceService.computeBalanceMap(GROUP_ID);

        // Payer: +100 - own share 33.34 = +66.66.
        assertThat(balances.get(1L)).isEqualByComparingTo("66.66");
        assertThat(balances.get(2L)).isEqualByComparingTo("-33.33");
        assertThat(balances.get(3L)).isEqualByComparingTo("-33.33");
        assertSumIsZero(balances);
    }

    @Test
    void multiple_expenses_aggregate_correctly() {
        // Expense 10: user 1 paid 60, split equally among {1,2}: 30 / 30
        // Expense 11: user 2 paid 40, split equally among {2,3}: 20 / 20
        Expense e1 = expense(10L, 1L, "60.00");
        Expense e2 = expense(11L, 2L, "40.00");
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(GROUP_ID)).thenReturn(List.of(e1, e2));
        when(shareRepository.findByExpenseIdIn(any())).thenReturn(List.of(
                share(10L, 1L, "30.00"),
                share(10L, 2L, "30.00"),
                share(11L, 2L, "20.00"),
                share(11L, 3L, "20.00")));
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        Map<Long, BigDecimal> balances = balanceService.computeBalanceMap(GROUP_ID);

        // User 1: +60 - 30 = +30
        // User 2: +40 - 30 - 20 = -10
        // User 3: 0 - 20 = -20
        assertThat(balances.get(1L)).isEqualByComparingTo("30.00");
        assertThat(balances.get(2L)).isEqualByComparingTo("-10.00");
        assertThat(balances.get(3L)).isEqualByComparingTo("-20.00");
        assertSumIsZero(balances);
    }

    @Test
    void recorded_settlement_reduces_credit_and_debt() {
        // User 1 paid 100; equal split among {1,2,3}. Then user 2 settles 33.33 to user 1.
        Expense e = expense(10L, 1L, "100.00");
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(GROUP_ID)).thenReturn(List.of(e));
        when(shareRepository.findByExpenseIdIn(List.of(10L))).thenReturn(List.of(
                share(10L, 1L, "33.34"),
                share(10L, 2L, "33.33"),
                share(10L, 3L, "33.33")));
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(
                settlement(GROUP_ID, 2L, 1L, "33.33")));

        Map<Long, BigDecimal> balances = balanceService.computeBalanceMap(GROUP_ID);

        // User 1: 66.66 - 33.33 (received) = 33.33
        // User 2: -33.33 + 33.33 (paid back) = 0.00
        // User 3: still -33.33
        assertThat(balances.get(1L)).isEqualByComparingTo("33.33");
        assertThat(balances.get(2L)).isEqualByComparingTo("0.00");
        assertThat(balances.get(3L)).isEqualByComparingTo("-33.33");
        assertSumIsZero(balances);
    }

    @Test
    void getBalances_throws_when_caller_is_not_a_member() {
        when(memberRepository.existsByGroupIdAndUserId(GROUP_ID, 99L)).thenReturn(false);

        assertThatThrownBy(() -> balanceService.getBalances(GROUP_ID, 99L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not a member");
    }

    // -- helpers ---------------------------------------------------------

    private static GroupMember member(long groupId, long userId) {
        return new GroupMember(groupId, userId);
    }

    private static Expense expense(long id, long paidBy, String amount) {
        Expense e = new Expense(GROUP_ID, paidBy, "test", new BigDecimal(amount), SplitType.EQUAL);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static ExpenseShare share(long expenseId, long userId, String amount) {
        return new ExpenseShare(expenseId, userId, new BigDecimal(amount));
    }

    private static Settlement settlement(long groupId, long fromUserId, long toUserId, String amount) {
        return new Settlement(groupId, fromUserId, toUserId, new BigDecimal(amount), null);
    }

    private static void assertSumIsZero(Map<Long, BigDecimal> balances) {
        BigDecimal sum = balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0.00");
    }
}
