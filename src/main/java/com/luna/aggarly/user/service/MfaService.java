package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.request.Mfa;
import com.luna.aggarly.user.dto.request.MfaConfirmation;
import com.luna.aggarly.user.dto.response.AuthResponse;

import java.util.UUID;

public interface MfaService {
    AuthResponse create(UUID UserId);
    String CreateMfa(UUID userId,String secret);
    Mfa get(String token);
    MfaConfirmation getMfaConfirm(String token);
    void delete(String token);
}
