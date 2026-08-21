package com.luna.aggarly.user.dto.response;

import lombok.Builder;

@Builder
public record RequestMfaResponse(
        String token,
        String uri,
        String qr
) {
}
