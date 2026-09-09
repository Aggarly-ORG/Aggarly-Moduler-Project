package com.luna.aggarly.host.dto;

public record UpdateTurnoverStatusRequest(
        String status,
        String notes,
        Double acousticDbReading,
        Boolean silenceCertified
) {}