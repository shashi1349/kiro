package com.shashikiranreddy.splitwise.balance.api;

import com.shashikiranreddy.splitwise.balance.application.BalanceService;
import com.shashikiranreddy.splitwise.balance.application.BalanceService.UserBalance;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only balance view for a group.
 */
@RestController
@RequestMapping("/groups/{groupId}/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public List<UserBalance> getBalances(@PathVariable Long groupId,
                                         @AuthenticationPrincipal Long userId) {
        return balanceService.getBalances(groupId, userId);
    }
}
