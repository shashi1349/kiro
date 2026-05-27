package com.shashikiranreddy.splitwise.settlement.application;

import com.shashikiranreddy.splitwise.balance.application.BalanceService;
import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.BadRequestException;
import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ForbiddenException;
import com.shashikiranreddy.splitwise.group.domain.GroupMemberRepository;
import com.shashikiranreddy.splitwise.settlement.application.DebtSimplifier.Transfer;
import com.shashikiranreddy.splitwise.settlement.domain.Settlement;
import com.shashikiranreddy.splitwise.settlement.domain.SettlementRepository;
import com.shashikiranreddy.splitwise.user.domain.User;
import com.shashikiranreddy.splitwise.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Records manual settlements between members and produces optimized
 * settle-up suggestions via the {@link DebtSimplifier}.
 */
@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupMemberRepository memberRepository;
    private final BalanceService balanceService;
    private final DebtSimplifier debtSimplifier;
    private final UserRepository userRepository;

    public SettlementService(SettlementRepository settlementRepository,
                             GroupMemberRepository memberRepository,
                             BalanceService balanceService,
                             DebtSimplifier debtSimplifier,
                             UserRepository userRepository) {
        this.settlementRepository = settlementRepository;
        this.memberRepository = memberRepository;
        this.balanceService = balanceService;
        this.debtSimplifier = debtSimplifier;
        this.userRepository = userRepository;
    }

    public record SettlementView(Long id, Long groupId, Long fromUserId, String fromName,
                                 Long toUserId, String toName, BigDecimal amount,
                                 String note, Instant settledAt) {}

    public record SuggestedTransfer(Long fromUserId, String fromName,
                                    Long toUserId, String toName, BigDecimal amount) {}

    @Transactional
    public SettlementView record(Long groupId, Long fromUserId, Long toUserId,
                                 BigDecimal amount, String note, Long currentUserId) {
        requireMembership(groupId, currentUserId);
        if (fromUserId.equals(toUserId)) {
            throw new BadRequestException("A settlement must be between two different users.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Settlement amount must be greater than zero.");
        }
        if (!memberRepository.existsByGroupIdAndUserId(groupId, fromUserId)
                || !memberRepository.existsByGroupIdAndUserId(groupId, toUserId)) {
            throw new BadRequestException("Both parties must be members of this group.");
        }

        Settlement saved = settlementRepository.save(new Settlement(
                groupId, fromUserId, toUserId, amount.setScale(2), note));
        Map<Long, String> names = lookupNames(List.of(fromUserId, toUserId));
        return toView(saved, names);
    }

    @Transactional(readOnly = true)
    public List<SettlementView> list(Long groupId, Long currentUserId) {
        requireMembership(groupId, currentUserId);
        List<Settlement> rows = settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId);
        if (rows.isEmpty()) return List.of();
        Map<Long, String> names = lookupNames(rows.stream()
                .flatMap(s -> java.util.stream.Stream.of(s.getFromUserId(), s.getToUserId()))
                .toList());
        return rows.stream().map(s -> toView(s, names)).toList();
    }

    /**
     * Returns the optimized list of transfers required to bring every member's
     * balance to zero. Read-only — these transfers are not persisted until the
     * caller explicitly records them via {@link #record}.
     */
    @Transactional(readOnly = true)
    public List<SuggestedTransfer> suggestSettleUp(Long groupId, Long currentUserId) {
        requireMembership(groupId, currentUserId);
        Map<Long, BigDecimal> balances = balanceService.computeBalanceMap(groupId);
        List<Transfer> transfers = debtSimplifier.simplify(balances);
        if (transfers.isEmpty()) return List.of();
        Map<Long, String> names = lookupNames(transfers.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.fromUserId(), t.toUserId()))
                .toList());
        return transfers.stream()
                .map(t -> new SuggestedTransfer(
                        t.fromUserId(), names.getOrDefault(t.fromUserId(), "Unknown"),
                        t.toUserId(), names.getOrDefault(t.toUserId(), "Unknown"),
                        t.amount()))
                .toList();
    }

    private SettlementView toView(Settlement s, Map<Long, String> names) {
        return new SettlementView(s.getId(), s.getGroupId(),
                s.getFromUserId(), names.getOrDefault(s.getFromUserId(), "Unknown"),
                s.getToUserId(), names.getOrDefault(s.getToUserId(), "Unknown"),
                s.getAmount(), s.getNote(), s.getSettledAt());
    }

    private Map<Long, String> lookupNames(List<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
    }

    private void requireMembership(Long groupId, Long userId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group.");
        }
    }
}
