package com.shashikiranreddy.splitwise.expense.domain;

/**
 * How an expense is divided across participants.
 *
 * <ul>
 *   <li>{@link #EQUAL} — everyone listed pays the same share; cent rounding
 *       remainders are distributed to the first few participants.</li>
 *   <li>{@link #EXACT} — caller specifies the exact amount each participant owes.
 *       The sum must equal the expense total to the cent.</li>
 *   <li>{@link #PERCENT} — caller specifies a percentage per participant.
 *       The percentages must sum to 100; cent rounding is balanced afterwards.</li>
 * </ul>
 */
public enum SplitType {
    EQUAL,
    EXACT,
    PERCENT
}
