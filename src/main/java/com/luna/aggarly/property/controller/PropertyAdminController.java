package com.luna.aggarly.property.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.property.dto.request.RevisionRequestDto;
import com.luna.aggarly.property.dto.response.LiveGuestResidentsResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.dto.response.SanctuaryAuditSummaryResponse;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Sanctuary Admin", description = "Nocturnal Sanctuary QA Moderation & Occupancy Analytics APIs")
public class PropertyAdminController {

    private final PropertyService propertyService;

    @GetMapping("/properties")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin paginated query of properties by status (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> listAdminProperties(
            @RequestParam(required = false) PropertyStatus status,
            Pageable pageable) {
        Page<PropertyResponse> page = propertyService.listProperties(status, pageable);
        return ApiResponse.paged(page, "Admin property catalogue retrieved").toResponseEntity();
    }

    @PostMapping("/properties/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve and publish sanctuary to active catalog (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyResponse>> publishProperty(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal curator) {
        UUID curatorId = curator != null ? curator.getUserId() : null;
        PropertyResponse response = propertyService.publishProperty(id, curatorId);
        return ApiResponse.ok(response, "Sanctuary approved and published to catalog").toResponseEntity();
    }

    @PostMapping("/properties/{id}/feature")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Toggle property featured moon-phase spotlight (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyResponse>> toggleFeatured(
            @PathVariable UUID id,
            @RequestParam boolean featured) {
        PropertyResponse response = propertyService.toggleFeatured(id, featured);
        return ApiResponse.ok(response, "Sanctuary spotlight status updated").toResponseEntity();
    }

    @PostMapping("/properties/{id}/request-revision")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Request host revision with calibration notes (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> requestRevision(
            @PathVariable UUID id,
            @Valid @RequestBody RevisionRequestDto directive) {
        propertyService.requestRevision(id, directive.getNotes());
        return ApiResponse.<Void>empty("Revision request dispatched to host").toResponseEntity();
    }

    @GetMapping("/sanctuaries/audit-summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get sanctuary portfolio audit compliance summary (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<SanctuaryAuditSummaryResponse>> getSanctuaryAuditSummary() {
        SanctuaryAuditSummaryResponse summary = propertyService.getSanctuaryAuditSummary();
        return ApiResponse.ok(summary, "Sanctuary audit summary retrieved").toResponseEntity();
    }

    @GetMapping("/occupancy/live-residents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get live in-house guest residents and geographic hub distribution (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<LiveGuestResidentsResponse>> getLiveGuestResidents() {
        LiveGuestResidentsResponse residents = propertyService.getLiveGuestResidents();
        return ApiResponse.ok(residents, "Live guest residents telemetry retrieved").toResponseEntity();
    }
}
