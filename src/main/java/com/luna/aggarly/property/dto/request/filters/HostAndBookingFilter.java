package com.luna.aggarly.property.dto.request.filters;

public record HostAndBookingFilter ( Boolean instantBookOnly, Boolean superhostOnly, com.luna.aggarly.property.entity.enums.CancellationPolicyType cancellationPolicy ) {}
