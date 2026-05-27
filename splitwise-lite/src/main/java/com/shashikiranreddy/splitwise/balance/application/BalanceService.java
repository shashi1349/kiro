package com.shashikiranreddy.splitwise.balance.application;

import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ForbiddenException;
import com.shashikiranreddy.splitwise.expense.domain.Expense;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseRepository;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseShare;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseShareRepository;
import com.shashikiranreddy.splitwise.group.domain.GroupMember;
import com.shashikiranreddy.splitwise.group.domain.GroupMemberRepository;
import com.shashikiranreddy.splitwise.settlement.domain.Settlement;
import com.shashikiranreddy.splitwise.settlement.domain.SettlementRepository;
import com.shashikiranreddy.splitwise.user.domain.User;
import com.shashikiranreddy.splitwise.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes the per-user net balance for an expense group.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Initialize every group member's balance to zero.</li>
 *   <li>For each expense: credit the payer with the full amount, debit each
 *       participant by their share. If shares sum to the expense total,
 *       these deltas always net to zero across the group.</li>
 * </ol>
 *
 * <p>Sign convention:
 * <ul>
 *   <li>{@code balance > 0} — this user is owed money (creditor)</li>
 *   <li>{@code balance < 0} — this user owes money (debtor)</li>
 *   <li>{@code balance == 0} — settled up</li>
 * </ul>
 *
 * <p>Module 6 extends this by also subtracting recorded settlements.
 */
@Service
public class BalanceService {

    private final GroupMemberRepository memberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository shareRepository;
    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;

    public BalanceService(GroupMemberRepository memberRepository,
                          ExpenseRepository expenseRepository,
                          ExpenseShareRepository shareRepository,
                          SettlementRepository settlementRepository,
                          UserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.settlementRepository = settlementRepository;
        this.userRepository = userRepository;
    }

    public record UserBalance(Long userId, String name, BigDecimal balance) {}

    /**
     * Returns the raw {@code userId -> netBalance} map for a group, with all
     * members included even if their balance is zero.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> computeBalanceMap(Long groupId) {
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (GroupMember m : memberRepository.findByGroupId(groupId)) {
            balances.put(m.getUserId(), BigDecimal.ZERO);
        }

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        if (expenses.isEmpty()) {
            // Still need to apply settlements (a group can have settlements without expenses).
            for (Settlement s : settlementRepository.findByGroupId(groupId)) {
                balances.merge(s.getFromUserId(), s.getAmount(), BigDecimal::add);
                balances.merge(s.getToUserId(), s.getAmount().negate(), BigDecimal::add);
            }
            return balances;
        }

        List<Long> expenseIds = expenses.stream().map(Expense::getId).toList();
        Map<Long, List<ExpenseShare>> sharesByExpense = shareRepository.findByExpenseIdIn(expenseIds)
                .stream().collect(Collectors.groupingBy(ExpenseShare::getExpenseId));

        for (Expense e : expenses) {
            balances.merge(e.getPaidBy(), e.getAmount(), BigDecimal::add);
            for (ExpenseShare s : sharesByExpense.getOrDefault(e.getId(), List.of())) {
                balances.merge(s.getUserId(), s.getShareAmount().negate(), BigDecimal::add);
            }
        }

        // Apply recorded settlements: a settlement from F to T moves money from
        // F's debt-side back toward zero and from T's credit-side back toward zero.
        for (Settlement s : settlementRepository.findByGroupId(groupId)) {
            balances.merge(s.getFromUserId(), s.getAmount(), BigDecimal::add);
            balances.merge(s.getToUserId(), s.getAmount().negate(), BigDecimal::add);
        }
        return balances;
    }

    /**
     * Returns the same balances as {@link #computeBalanceMap} but enriched with
     * user names and ordered creditor-first, then largest debtors. Authorization:
     * the caller must be a member of the group.
     */
    @Transactional(readOnly = true)
    public List<UserBalance> getBalances(Long groupId, Long currentUserId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw new ForbiddenException("You are not a member of this group.");
        }
        Map<Long, BigDecimal> balances = computeBalanceMap(groupId);
        if (balances.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameById = userRepository.findAllById(balances.keySet()).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a, LinkedHashMap::new));
        return balances.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> new UserBalance(e.getKey(),
                        nameById.getOrDefault(e.getKey(), "Unknown"),
                        e.getValue()))
                .toList();
    }
}
