package com.luna.aggarly.host.service;

import com.luna.aggarly.host.dto.HostFinancialsSummaryDto;
import com.luna.aggarly.host.dto.SettlementLedgerItemDto;
import com.luna.aggarly.host.dto.TaxStatementDto;

import java.util.List;
import java.util.UUID;

public interface HostFinancialService {
    HostFinancialsSummaryDto getSummary(UUID hostId);
    List<SettlementLedgerItemDto> getLedger(UUID hostId, String status);
    List<TaxStatementDto> getTaxStatements(UUID hostId);
}