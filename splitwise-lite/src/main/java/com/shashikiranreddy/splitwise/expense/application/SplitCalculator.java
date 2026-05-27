package com.shashikiranreddy.splitwise.expense.application;

import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.BadRequestException;
import com.shashikiranreddy.splitwise.expense.domain.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes per-user share amounts for an expense, given a total and a split rule.
 *
 * <p>All money is handled as {@link BigDecimal} with scale 2. To avoid floating-point
 * drift the calculator works in integer cents internally and converts back at the end,
 * so the sum of returned shares is guaranteed to equal the original total to the cent.
 *
 * <p>Supported split types: {@link SplitType#EQUAL}, {@link SplitType#EXACT},
 * {@link SplitType#PERCENT}.
 */
@Component
public class SplitCalculator {

    /** Caller-supplied input for a single participant. {@code value} is unused for EQUAL. */
    public record Input(Long userId, BigDecimal value) {}

    /** Computed share for a single participant. */
    public record Share(Long userId, BigDecimal amount) {}

    public List<Share> split(BigDecimal total, SplitType type, List<Input> inputs) {
        validateCommon(total, inputs);
        return switch (type) {
            case EQUAL -> equalSplit(total, inputs);
            case EXACT -> exactSplit(total, inputs);
            case PERCENT -> percentSplit(total, inputs);
        };
    }

    // -- EQUAL --------------------------------------------------------------

    private List<Share> equalSplit(BigDecimal total, List<Input> inputs) {
        int n = inputs.size();
        long totalCents = toCents(total);
        long base = totalCents / n;
        int remainderCents = (int) (totalCents % n);

        List<Share> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long cents = base + (i < remainderCents ? 1 : 0);
            shares.add(new Share(inputs.get(i).userId(), fromCents(cents)));
        }
        return shares;
    }

    // -- EXACT --------------------------------------------------------------

    private List<Share> exactSplit(BigDecimal total, List<Input> inputs) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Input in : inputs) {
            requireNonNullValue(in, "exact amount");
            if (in.value().signum() < 0) {
                throw new BadRequestException("Exact share amounts must be non-negative.");
            }
            sum = sum.add(in.value());
        }
        BigDecimal totalScaled = total.setScale(2, RoundingMode.HALF_UP);
        if (sum.setScale(2, RoundingMode.HALF_UP).compareTo(totalScaled) != 0) {
            throw new BadRequestException(
                    "Exact shares sum to " + sum + " but expense total is " + totalScaled + ".");
        }
        return inputs.stream()
                .map(in -> new Share(in.userId(), in.value().setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    // -- PERCENT ------------------------------------------------------------

    private List<Share> percentSplit(BigDecimal total, List<Input> inputs) {
        BigDecimal pctSum = BigDecimal.ZERO;
        for (Input in : inputs) {
            requireNonNullValue(in, "percentage");
            if (in.value().signum() < 0
                    || in.value().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("Each percentage must be between 0 and 100.");
            }
            pctSum = pctSum.add(in.value());
        }
        if (pctSum.setScale(2, RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(100).setScale(2)) != 0) {
            throw new BadRequestException(
                    "Percentages must sum to 100 but summed to " + pctSum + ".");
        }

        long totalCents = toCents(total);
        long[] centShares = new long[inputs.size()];
        long allocated = 0;
        for (int i = 0; i < inputs.size(); i++) {
            // floor(total * pct / 100) in cents
            BigDecimal exact = total.multiply(inputs.get(i).value())
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            centShares[i] = exact.movePointRight(2).setScale(0, RoundingMode.FLOOR).longValueExact();
            allocated += centShares[i];
        }

        long remainder = totalCents - allocated; // at most n-1 in absolute value
        for (int i = 0; remainder != 0; i = (i + 1) % inputs.size()) {
            if (remainder > 0) { centShares[i] += 1; remainder--; }
            else               { centShares[i] -= 1; remainder++; }
        }

        List<Share> result = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            result.add(new Share(inputs.get(i).userId(), fromCents(centShares[i])));
        }
        return result;
    }

    // -- helpers ------------------------------------------------------------

    private void validateCommon(BigDecimal total, List<Input> inputs) {
        if (total == null || total.signum() <= 0) {
            throw new BadRequestException("Total amount must be greater than zero.");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new BadRequestException("At least one participant is required.");
        }
        Set<Long> seen = new HashSet<>();
        for (Input in : inputs) {
            if (in.userId() == null) {
                throw new BadRequestException("Each split entry must include a userId.");
            }
            if (!seen.add(in.userId())) {
                throw new BadRequestException("Duplicate userId " + in.userId() + " in splits.");
            }
        }
    }

    private void requireNonNullValue(Input in, String label) {
        if (in.value() == null) {
            throw new BadRequestException("Missing " + label + " for user " + in.userId() + ".");
        }
    }

    private long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
