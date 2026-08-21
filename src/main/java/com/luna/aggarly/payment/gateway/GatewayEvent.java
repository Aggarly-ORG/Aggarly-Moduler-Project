package com.luna.aggarly.payment.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayEvent {
    private String eventId;
    private String eventType;
    private String payload;
}
