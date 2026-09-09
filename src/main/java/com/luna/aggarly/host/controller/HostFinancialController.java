package com.luna.aggarly.host.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.host.dto.HostFinancialsSummaryDto;
import com.luna.aggarly.host.dto.SettlementLedgerItemDto;
import com.luna.aggarly.host.dto.TaxStatementDto;
import com.luna.aggarly.host.service.HostFinancialService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/host/financials")
@RequiredArgsConstructor
@Tag(name = "Host Financials", description = "Curator Payouts, Settlement Ledgers & Tax Telemetry")
public class HostFinancialController {

    private final HostFinancialService financialService;

    @PreAuthorize("hasRole('HOST')")
    @GetMapping("/summary")
    @Operation(summary = "Get host financials, escrow balances and next payout estimate", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<HostFinancialsSummaryDto>> getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        HostFinancialsSummaryDto summary = financialService.getSummary(principal.getUserId());
        return ApiResponse.ok(summary, "Financial summary retrieved").toResponseEntity();
    }

    @PreAuthorize("hasRole('HOST')")
    @GetMapping("/ledger")
    @Operation(summary = "Get host itemized booking settlement ledger", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<SettlementLedgerItemDto>>> getLedger(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status) {
        List<SettlementLedgerItemDto> ledger = financialService.getLedger(principal.getUserId(), status);
        return ApiResponse.ok(ledger, "Settlement ledger retrieved").toResponseEntity();
    }

    @PreAuthorize("hasRole('HOST')")
    @GetMapping("/tax-statements")
    @Operation(summary = "Get host annual/quarterly tax certificates & statements", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<TaxStatementDto>>> getTaxStatements(@AuthenticationPrincipal UserPrincipal principal) {
        List<TaxStatementDto> statements = financialService.getTaxStatements(principal.getUserId());
        return ApiResponse.ok(statements, "Tax statements retrieved").toResponseEntity();
    }
}