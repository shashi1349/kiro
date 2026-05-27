package com.shashikiranreddy.splitwise.expense.application;

import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.BadRequestException;
import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ForbiddenException;
import com.shashikiranreddy.splitwise.expense.application.SplitCalculator.Share;
import com.shashikiranreddy.splitwise.expense.domain.Expense;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseRepository;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseShare;
import com.shashikiranreddy.splitwise.expense.domain.ExpenseShareRepository;
import com.shashikiranreddy.splitwise.expense.domain.SplitType;
import com.shashikiranreddy.splitwise.group.domain.GroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the lifecycle of an expense: validates that the payer and every
 * split participant belong to the group, runs the {@link SplitCalculator} to
 * produce per-user shares, and persists the expense and its shares atomically.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository shareRepository;
    private final GroupMemberRepository memberRepository;
    private final SplitCalculator splitCalculator;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseShareRepository shareRepository,
                          GroupMemberRepository memberRepository,
                          SplitCalculator splitCalculator) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.memberRepository = memberRepository;
        this.splitCalculator = splitCalculator;
    }

    public record AddExpenseCommand(Long groupId, Long paidBy, String description,
                                    BigDecimal amount, SplitType splitType,
                                    List<SplitCalculator.Input> participants) {}

    public record ShareView(Long userId, BigDecimal amount) {}

    public record ExpenseView(Long id, Long groupId, Long paidBy, String description,
                              BigDecimal amount, SplitType splitType,
                              Instant createdAt, List<ShareView> shares) {}

    @Transactional
    public ExpenseView addExpense(AddExpenseCommand cmd, Long currentUserId) {
        requireMembership(cmd.groupId(), currentUserId);
        if (!memberRepository.existsByGroupIdAndUserId(cmd.groupId(), cmd.paidBy())) {
            throw new BadRequestException("Payer is not a member of this group.");
        }
        for (SplitCalculator.Input p : cmd.participants()) {
            if (!memberRepository.existsByGroupIdAndUserId(cmd.groupId(), p.userId())) {
                throw new BadRequestException(
                        "Participant " + p.userId() + " is not a member of this group.");
            }
        }

        List<Share> shares = splitCalculator.split(cmd.amount(), cmd.splitType(), cmd.participants());

        Expense expense = expenseRepository.save(new Expense(
                cmd.groupId(), cmd.paidBy(), cmd.description().trim(),
                cmd.amount(), cmd.splitType()));

        List<ExpenseShare> shareEntities = shares.stream()
                .map(s -> new ExpenseShare(expense.getId(), s.userId(), s.amount()))
                .toList();
        shareRepository.saveAll(shareEntities);

        return new ExpenseView(expense.getId(), expense.getGroupId(), expense.getPaidBy(),
                expense.getDescription(), expense.getAmount(), expense.getSplitType(),
                expense.getCreatedAt(),
                shares.stream().map(s -> new ShareView(s.userId(), s.amount())).toList());
    }

    @Transactional(readOnly = true)
    public List<ExpenseView> listExpenses(Long groupId, Long currentUserId) {
        requireMembership(groupId, currentUserId);
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        if (expenses.isEmpty()) {
            return List.of();
        }
        List<Long> ids = expenses.stream().map(Expense::getId).toList();
        var sharesByExpense = shareRepository.findByExpenseIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(ExpenseShare::getExpenseId));
        return expenses.stream()
                .map(e -> new ExpenseView(e.getId(), e.getGroupId(), e.getPaidBy(),
                        e.getDescription(), e.getAmount(), e.getSplitType(), e.getCreatedAt(),
                        sharesByExpense.getOrDefault(e.getId(), List.of()).stream()
                                .map(s -> new ShareView(s.getUserId(), s.getShareAmount()))
                                .toList()))
                .toList();
    }

    private void requireMembership(Long groupId, Long userId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group.");
        }
    }
}
