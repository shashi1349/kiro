package com.shashikiranreddy.splitwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Splitwise-Lite: a REST API for tracking group expenses.
 *
 * <p>Inspired by Splitwise, this service lets users create groups, add expenses
 * paid by a single member, split them across the group (equally, by exact amounts,
 * or by percentage), and compute who owes whom. Its core algorithm simplifies
 * the resulting debts into the minimum number of transactions needed to settle up.
 */
@SpringBootApplication
public class SplitwiseLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SplitwiseLiteApplication.class, args);
    }
}
