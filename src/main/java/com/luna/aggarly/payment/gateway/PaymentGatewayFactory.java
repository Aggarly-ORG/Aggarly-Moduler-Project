package com.luna.aggarly.payment.gateway;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayFactory {

    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(
                        gateway -> gateway.getProviderName().toLowerCase(),
                        Function.identity()
                ));
    }

    public PaymentGateway getGateway(String providerName) {
        PaymentGateway gateway = gateways.get(providerName.toLowerCase());
        if (gateway == null) {
            throw new IllegalArgumentException("Unsupported payment provider: " + providerName);
        }
        return gateway;
    }

    public PaymentGateway getDefaultGateway() {
        return getGateway("stripe");
    }

    public List<String> getSupportedProviders() {
        return gateways.keySet().stream().toList();
    }
}
