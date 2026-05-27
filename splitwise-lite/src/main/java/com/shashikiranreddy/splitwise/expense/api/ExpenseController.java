package com.shashikiranreddy.splitwise.expense.api;

import com.shashikiranreddy.splitwise.expense.application.ExpenseService;
import com.shashikiranreddy.splitwise.expense.application.ExpenseService.AddExpenseCommand;
import com.shashikiranreddy.splitwise.expense.application.ExpenseService.ExpenseView;
import com.shashikiranreddy.splitwise.expense.application.SplitCalculator;
import com.shashikiranreddy.splitwise.expense.domain.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Expense endpoints scoped to a specific group.
 */
@RestController
@RequestMapping("/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    public record SplitEntry(@NotNull Long userId, BigDecimal value) {}

    public record AddExpenseRequest(
            @NotNull Long paidBy,
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
            @NotNull SplitType splitType,
            @NotEmpty List<@Valid SplitEntry> splits) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseView add(@PathVariable Long groupId,
                           @Valid @RequestBody AddExpenseRequest request,
                           @AuthenticationPrincipal Long userId) {
        var participants = request.splits().stream()
                .map(s -> new SplitCalculator.Input(s.userId(), s.value()))
                .toList();
        var cmd = new AddExpenseCommand(groupId, request.paidBy(), request.description(),
                request.amount(), request.splitType(), participants);
        return expenseService.addExpense(cmd, userId);
    }

    @GetMapping
    public List<ExpenseView> list(@PathVariable Long groupId,
                                  @AuthenticationPrincipal Long userId) {
        return expenseService.listExpenses(groupId, userId);
    }
}
