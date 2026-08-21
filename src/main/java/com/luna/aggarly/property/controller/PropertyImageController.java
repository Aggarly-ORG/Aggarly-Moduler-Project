package com.luna.aggarly.property.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.property.dto.request.AddPropertyImageRequest;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.service.PropertyImageService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller managing property image attachments, ordering, and cover photo assignment.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/images")
@RequiredArgsConstructor
@Tag(name = "Property Images", description = "Manage property images via URL references")
public class PropertyImageController {

    private final PropertyImageService propertyImageService;

    @PreAuthorize("hasRole('HOST')")
    @PostMapping
    @Operation(summary = "Add an image URL to a property (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyImageResponse>> addImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId,
            @Valid @RequestBody AddPropertyImageRequest request) {
        PropertyImageResponse response = propertyImageService.addImage(propertyId, principal.getUserId(), request);
        return ApiResponse.created(response, "Property image added successfully").toResponseEntity();
    }

    @PreAuthorize("hasRole('HOST')")
    @PutMapping("/{imageId}/cover")
    @Operation(summary = "Set an image as the cover photo (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyImageResponse>> setCoverImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId,
            @PathVariable UUID imageId) {
        PropertyImageResponse response = propertyImageService.setCoverImage(propertyId, principal.getUserId(), imageId);
        return ApiResponse.ok(response, "Cover image updated successfully").toResponseEntity();
    }

    @PreAuthorize("hasRole('HOST')")
    @DeleteMapping("/{imageId}")
    @Operation(summary = "Delete an image from a property (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId,
            @PathVariable UUID imageId) {
        propertyImageService.deleteImage(propertyId, principal.getUserId(), imageId);
        return ApiResponse.<Void>empty("Property image deleted successfully").toResponseEntity();
    }
}
