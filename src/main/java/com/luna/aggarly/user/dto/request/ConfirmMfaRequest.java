package com.luna.aggarly.user.dto.request;

public record ConfirmMfaRequest(String token,String totpCode) {
}
