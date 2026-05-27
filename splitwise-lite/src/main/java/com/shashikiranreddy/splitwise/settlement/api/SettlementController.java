package com.shashikiranreddy.splitwise.settlement.api;

import com.shashikiranreddy.splitwise.settlement.application.SettlementService;
import com.shashikiranreddy.splitwise.settlement.application.SettlementService.SettlementView;
import com.shashikiranreddy.splitwise.settlement.application.SettlementService.SuggestedTransfer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
 * Endpoints for recording manual settlements and fetching the optimized
 * settle-up plan produced by {@link com.shashikiranreddy.splitwise.settlement.application.DebtSimplifier}.
 */
@RestController
@RequestMapping("/groups/{groupId}")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    public record RecordSettlementRequest(
            @NotNull Long fromUserId,
            @NotNull Long toUserId,
            @NotNull @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
            @Size(max = 200) String note) {}

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementView record(@PathVariable Long groupId,
                                 @Valid @RequestBody RecordSettlementRequest request,
                                 @AuthenticationPrincipal Long userId) {
        return settlementService.record(groupId, request.fromUserId(),
                request.toUserId(), request.amount(), request.note(), userId);
    }

    @GetMapping("/settlements")
    public List<SettlementView> list(@PathVariable Long groupId,
                                     @AuthenticationPrincipal Long userId) {
        return settlementService.list(groupId, userId);
    }

    @GetMapping("/settle-up")
    public List<SuggestedTransfer> suggestSettleUp(@PathVariable Long groupId,
                                                   @AuthenticationPrincipal Long userId) {
        return settlementService.suggestSettleUp(groupId, userId);
    }
}
