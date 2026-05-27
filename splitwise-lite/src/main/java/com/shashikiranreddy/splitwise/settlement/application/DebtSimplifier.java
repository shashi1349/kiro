package com.shashikiranreddy.splitwise.settlement.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Reduces a group's net-balance map into a list of transfers that settles
 * everyone, using as few transactions as possible.
 *
 * <h3>Algorithm — two-heap greedy</h3>
 * <pre>
 *   creditors = max-heap by amount owed to them   (positive balances)
 *   debtors   = max-heap by amount they still owe (absolute value of negative balances)
 *
 *   while creditors and debtors are both non-empty:
 *       C = creditors.pop()
 *       D = debtors.pop()
 *       transfer = min(C, D)
 *       record Transfer(from = D.user, to = C.user, amount = transfer)
 *       if C - transfer > 0: push (C - transfer) back
 *       if D - transfer > 0: push (D - transfer) back
 * </pre>
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>Produces at most {@code N - 1} transfers when {@code N} users have
 *       a non-zero balance, because each iteration zeroes out at least one user.</li>
 *   <li>Time complexity: {@code O(N log N)}.</li>
 *   <li>The general "minimum-cash-flow" problem is NP-hard. This greedy is the
 *       practical choice used by real apps (including Splitwise) — it is optimal
 *       in most everyday cases and never far from optimal otherwise.</li>
 * </ul>
 *
 * <p>All math is done in integer cents internally, so the returned transfer
 * amounts always sum exactly to the absolute total credit (= absolute total debt).
 */
@Component
public class DebtSimplifier {

    public record Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {}

    /** Internal node: a positive amount in cents, owned by some user. */
    private record Node(long cents, Long userId) {}

    public List<Transfer> simplify(Map<Long, BigDecimal> netBalances) {
        Comparator<Node> byCentsDesc = (a, b) -> Long.compare(b.cents(), a.cents());
        PriorityQueue<Node> creditors = new PriorityQueue<>(byCentsDesc);
        PriorityQueue<Node> debtors   = new PriorityQueue<>(byCentsDesc);

        for (Map.Entry<Long, BigDecimal> e : netBalances.entrySet()) {
            long cents = e.getValue().setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2).longValueExact();
            if (cents > 0)      creditors.offer(new Node(cents, e.getKey()));
            else if (cents < 0) debtors.offer(new Node(-cents, e.getKey()));
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Node c = creditors.poll();
            Node d = debtors.poll();
            long settle = Math.min(c.cents(), d.cents());

            transfers.add(new Transfer(d.userId(), c.userId(),
                    BigDecimal.valueOf(settle, 2)));

            if (c.cents() > settle) creditors.offer(new Node(c.cents() - settle, c.userId()));
            if (d.cents() > settle) debtors.offer(new Node(d.cents() - settle, d.userId()));
        }
        return transfers;
    }
}
