package com.luna.aggarly.help.dto;

public record CreateHelpSignalRequest(
        String residencyRef,
        String situationType,
        String description,
        String phone
) {}