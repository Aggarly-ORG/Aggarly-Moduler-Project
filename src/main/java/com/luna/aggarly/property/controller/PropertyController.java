package com.luna.aggarly.property.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.property.dto.request.CreatePropertyRequest;
import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.dto.request.UpdatePropertyRequest;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.luna.aggarly.property.entity.enums.PropertyStatus;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing property listings, search, updates, and host operations.
 */
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Tag(name = "Property", description = "Property Management APIs")
public class PropertyController {

    private final PropertyService propertyService;
    private final com.luna.aggarly.availability.service.AvailabilityService availabilityService;

    @GetMapping("/multi-calendar")
    @Operation(summary = "Batch retrieve calendar date grids and moon illumination percentages across sanctuaries (Public)")
    public ResponseEntity<ApiResponse<com.luna.aggarly.availability.dto.MultiSanctuaryCalendarResponse>> getMultiCalendar(
            @RequestParam List<UUID> propertyIds,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to) {
        com.luna.aggarly.availability.dto.MultiSanctuaryCalendarResponse response = availabilityService.getMultiCalendar(propertyIds, from, to);
        return ApiResponse.ok(response, "Multi-sanctuary calendar retrieved successfully").toResponseEntity();
    }

    @GetMapping
    @Operation(summary = "List properties with optional status filtering (Public / Admin)")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> listProperties(
            @RequestParam(required = false) PropertyStatus status,
            Pageable pageable) {
        Page<PropertyResponse> page = propertyService.listProperties(status, pageable);
        return ApiResponse.paged(page, "Properties retrieved successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PostMapping
    @Operation(summary = "Create a new property listing (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyResponse>> createProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePropertyRequest request) {
        PropertyResponse response = propertyService.createProperty(principal.getUserId(), request);
        return ApiResponse.created(response, "Property created successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PutMapping("/{propertyId}")
    @Operation(summary = "Update an existing property listing (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyResponse>> updateProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId,
            @Valid @RequestBody UpdatePropertyRequest request) {
        PropertyResponse response = propertyService.updateProperty(propertyId, principal.getUserId(), request);
        return ApiResponse.ok(response, "Property updated successfully").toResponseEntity();
    }

    @GetMapping("/{propertyId}")
    @Operation(summary = "Get property details by ID (Public)")
    public ResponseEntity<ApiResponse<PropertyResponse>> getPropertyById(@PathVariable UUID propertyId) {
        PropertyResponse response = propertyService.getPropertyById(propertyId);
        return ApiResponse.ok(response, "Property details retrieved successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @DeleteMapping("/{propertyId}")
    @Operation(summary = "Delete (soft-delete) a property listing (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId) {
        propertyService.deleteProperty(propertyId, principal.getUserId());
        return ApiResponse.<Void>empty("Property deleted successfully").toResponseEntity();
    }

    @GetMapping("/search")
    @Operation(summary = "Search properties with complex filters (Public)")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> searchProperties(
            @Valid @ModelAttribute PropertySearchRequest searchRequest,
            @PageableDefault(size = 10, sort = "avgRating", direction = Sort.Direction.DESC) Pageable pageable) {
        Slice<PropertyResponse> response = propertyService.searchProperties(searchRequest, pageable);
        return ApiResponse.sliced(response, "Properties matching search criteria retrieved").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST')")
    @GetMapping("/me")
    @Operation(summary = "Get all properties owned by the current HOST", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> getMyProperties(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        Page<PropertyResponse> response = propertyService.getHostProperties(principal.getUserId(), pageable);
        return ApiResponse.paged(response, "Host properties retrieved successfully").toResponseEntity();
    }
}
