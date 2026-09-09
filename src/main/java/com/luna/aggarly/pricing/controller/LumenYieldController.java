package com.luna.aggarly.pricing.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Dynamic Celestial & Moon Yield Pricing APIs")
public class LumenYieldController {

    private static volatile boolean lumenYieldEnabled = true;
    private static volatile double currentMultiplier = 1.18;

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PostMapping("/lumen-yield-toggle")
    @Operation(summary = "Toggle automatic dynamic moon-phase pricing yield (+18% during apogee / waxing gibbous)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLumenYield(@RequestBody Map<String, Object> payload) {
        if (payload.containsKey("enabled")) {
            lumenYieldEnabled = Boolean.parseBoolean(String.valueOf(payload.get("enabled")));
        }
        if (payload.containsKey("multiplier")) {
            currentMultiplier = Double.parseDouble(String.valueOf(payload.get("multiplier")));
        }

        Map<String, Object> result = Map.of(
                "enabled", lumenYieldEnabled,
                "multiplier", currentMultiplier,
                "message", "Lumen yield dynamic pricing state updated successfully"
        );
        return ApiResponse.ok(result, "Lumen dynamic pricing updated").toResponseEntity();
    }
}