package com.luna.aggarly.property.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.property.dto.request.CreateAmenityRequest;
import com.luna.aggarly.property.dto.response.AmenityResponse;
import com.luna.aggarly.property.service.AmenityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/amenities")
@RequiredArgsConstructor
@Tag(name = "Amenity", description = "Amenity Management APIs")
public class AmenityController {

    private final AmenityService amenityService;

    @GetMapping
    @Operation(summary = "Get all available amenities (Public)")
    public ResponseEntity<ApiResponse<List<AmenityResponse>>> getAllAmenities() {
        List<AmenityResponse> amenities = amenityService.getAllAmenities();
        return ApiResponse.ok(amenities, "Amenities retrieved successfully").toResponseEntity();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Operation(summary = "Create a new amenity (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AmenityResponse>> createAmenity(@Valid @RequestBody CreateAmenityRequest request) {
        AmenityResponse response = amenityService.createAmenity(request);
        return ApiResponse.created(response, "Amenity created successfully").toResponseEntity();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{amenityId}")
    @Operation(summary = "Delete an amenity (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteAmenity(@PathVariable UUID amenityId) {
        amenityService.deleteAmenity(amenityId);
        return ApiResponse.<Void>empty("Amenity deleted successfully").toResponseEntity();
    }
}
