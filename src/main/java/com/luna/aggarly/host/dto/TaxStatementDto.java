package com.luna.aggarly.host.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxStatementDto(
        String id,
        int taxYear,
        String statementType,
        LocalDate issuedDate,
        BigDecimal totalGrossVolume,
        BigDecimal taxableEarnings,
        BigDecimal vatWithheld,
        String certificateRef,
        String downloadUrl
) {}