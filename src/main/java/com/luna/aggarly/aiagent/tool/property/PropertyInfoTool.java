package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyInfoTool implements Tool<PropertyInfoTool.Params, Map<String, Object>> {

    private static final Set<String> SECTIONS = Set.of("DETAILS", "AMENITIES", "RULES", "LOCATION", "HOST", "NEARBY");

    public record Params(
            @JsonPropertyDescription("UUID of the property to inspect.")
            UUID propertyId,

            @JsonPropertyDescription("Optional sections to include: DETAILS, AMENITIES, RULES, LOCATION, HOST, NEARBY. " +
                    "Defaults to all sections when omitted.")
            List<String> sections
    ) {}

    private final PropertyService propertyService;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.info";
    }

    @Override
    public String description() {
        return "Fetch information about a specific property by UUID through selectable sections: " +
                "DETAILS (description, capacity, pricing, photos), AMENITIES (full amenity list with categories), " +
                "RULES (house rules, check-in/out times, cancellation policy), LOCATION (address, coordinates, map link), " +
                "HOST (host identity, listings count) and NEARBY (attractions, dining, transit). " +
                "Request only the sections you need.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(Params params, UserPrincipal user) {
        log.info("Executing property.info for propertyId={}, sections={}", params.propertyId(), params.sections());

        if (params.propertyId() == null) {
            return ToolResult.failed("INVALID_PARAMS", "propertyId is required.");
        }

        PropertyResponse property;
        try {
            property = propertyService.getPropertyById(params.propertyId());
        } catch (Exception ex) {
            log.warn("Property {} not found: {}", params.propertyId(), ex.getMessage());
            return ToolResult.failed("PROPERTY_NOT_FOUND", "Property with ID " + params.propertyId() + " was not found.");
        }

        Set<String> requested = resolveSections(params.sections());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("propertyId", property.id());
        out.put("title", property.title());

        if (requested.contains("DETAILS")) {
            out.put("details", buildDetailsSection(property));
        }
        if (requested.contains("AMENITIES")) {
            out.put("amenities", buildAmenitiesSection(property));
        }
        if (requested.contains("RULES")) {
            out.put("rules", buildRulesSection(property));
        }
        if (requested.contains("LOCATION")) {
            out.put("location", buildLocationSection(property));
        }
        if (requested.contains("HOST")) {
            out.put("host", buildHostSection(property));
        }
        if (requested.contains("NEARBY")) {
            out.put("nearby", buildNearbySection(property));
        }

        return ToolResult.ok(out);
    }

    private Set<String> resolveSections(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return SECTIONS;
        }
        Set<String> resolved = new java.util.HashSet<>();
        for (String s : raw) {
            if (s != null) {
                String upper = s.trim().toUpperCase(Locale.ROOT);
                if (SECTIONS.contains(upper)) {
                    resolved.add(upper);
                }
            }
        }
        return resolved.isEmpty() ? SECTIONS : resolved;
    }

    private Map<String, Object> buildDetailsSection(PropertyResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("description", p.description());
        m.put("propertyType", p.propertyType() != null ? p.propertyType().name() : null);
        m.put("status", p.status() != null ? p.status().name() : null);
        m.put("basePrice", p.basePricePerNight() != null ? p.basePricePerNight().doubleValue() : 0.0);
        m.put("maxGuests", p.maxGuests());
        m.put("bedrooms", p.bedrooms());
        m.put("bathrooms", p.bathrooms());
        m.put("rating", p.avgRating() != null ? p.avgRating().doubleValue() : 0.0);
        m.put("reviewCount", p.reviewCount());
        if (p.images() != null) {
            m.put("images", p.images().stream().map(img -> Map.of(
                    "id", img.id() != null ? img.id() : "",
                    "url", img.objectKey() != null ? img.objectKey() : "",
                    "isCover", img.isCover()
            )).toList());
        }
        return m;
    }

    private Map<String, Object> buildAmenitiesSection(PropertyResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (p.amenities() == null || p.amenities().isEmpty()) {
            m.put("items", List.of());
            m.put("count", 0);
            return m;
        }
        Map<String, List<String>> byCategory = new LinkedHashMap<>();
        for (var a : p.amenities()) {
            String cat = a.category() != null ? a.category().name() : "GENERAL";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(a.name());
        }
        m.put("byCategory", byCategory);
        m.put("allItems", p.amenities().stream().map(a -> a.name()).toList());
        m.put("count", p.amenities().size());
        return m;
    }

    private Map<String, Object> buildRulesSection(PropertyResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checkInTime", "15:00");
        m.put("checkOutTime", "11:00");
        m.put("cancellationPolicy", p.cancellationPolicy() != null ? p.cancellationPolicy().name() : "MODERATE");
        m.put("houseRules", "Standard residential house rules apply.");
        return m;
    }

    private Map<String, Object> buildLocationSection(PropertyResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (p.address() != null) {
            var a = p.address();
            m.put("street", a.street());
            m.put("city", a.city());
            m.put("state", a.state());
            m.put("country", a.country());
            m.put("postalCode", a.zipCode());
        }
        if (p.latitude() != null && p.longitude() != null) {
            m.put("latitude", p.latitude().doubleValue());
            m.put("longitude", p.longitude().doubleValue());
            m.put("mapUrl", String.format(Locale.ROOT, "https://maps.google.com/?q=%.6f,%.6f",
                    p.latitude().doubleValue(), p.longitude().doubleValue()));
        }
        return m;
    }

    private Map<String, Object> buildHostSection(PropertyResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        UUID hostId = p.hostId();
        if (hostId == null) return m;

        m.put("hostId", hostId);
        Optional<User> hostOpt = userRepository.findById(hostId);
        if (hostOpt.isPresent()) {
            User h = hostOpt.get();
            String name = (h.getFirstName() != null ? h.getFirstName() : "") + " " +
                    (h.getLastName() != null ? h.getLastName() : "").trim();
            m.put("name", name.isBlank() ? "Aggarly Host" : name.trim());
            m.put("email", h.getEmail());
            m.put("phone", h.getPhone());
            m.put("profilePhotoUrl", h.getAvatarUrl());
            m.put("isVerified", h.isEmailVerified());
        }
        try {
            long totalListings = propertyRepository.findByHostId(hostId, Pageable.unpaged()).getTotalElements();
            m.put("totalListings", totalListings);
        } catch (Exception ignored) {}
        return m;
    }

    private Map<String, Object> buildNearbySection(PropertyResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        String city = p.address() != null ? p.address().city() : "this area";
        m.put("summary", "Top attractions and transit convenient to " + city);
        m.put("transportation", List.of(
                Map.of("type", "Metro / Transit", "name", "City Center Metro Station", "distance", "0.4 km"),
                Map.of("type", "Airport", "name", city + " International Airport", "distance", "22 km")
        ));
        m.put("attractions", List.of(
                Map.of("name", "Historic Old Town", "type", "Cultural", "distance", "1.2 km"),
                Map.of("name", "Central Park & Promenade", "type", "Park", "distance", "0.8 km")
        ));
        return m;
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(Map.class);
    }
}
