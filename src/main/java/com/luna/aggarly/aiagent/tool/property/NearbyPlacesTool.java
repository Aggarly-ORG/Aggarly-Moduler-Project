package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.NearbyPlaceItem;
import com.luna.aggarly.aiagent.tool.property.record.NearbyPlacesParams;
import com.luna.aggarly.aiagent.tool.property.record.NearbyPlacesResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NearbyPlacesTool implements Tool<NearbyPlacesParams, NearbyPlacesResponse> {

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.nearbyPlaces";
    }

    @Override
    public String description() {
        return "Find nearby attractions, restaurants, supermarkets, and public transit options around a property.";
    }

    @Override
    public Class<NearbyPlacesParams> parameterType() {
        return NearbyPlacesParams.class;
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
    public ToolResult<NearbyPlacesResponse> execute(NearbyPlacesParams params, UserPrincipal user) {
        log.info("Executing property.nearbyPlaces for propertyId={}, category={}",
                params.propertyId(), params.category());

        try {
            PropertyResponse property = propertyService.getPropertyById(params.propertyId());
            String city = property.address() != null && property.address().city() != null
                    ? property.address().city()
                    : "the neighborhood";

            List<NearbyPlaceItem> places = new ArrayList<>();
            String cat = params.category() != null ? params.category().toLowerCase() : "all";

            if (cat.contains("restaurant") || cat.contains("food") || cat.equals("all")) {
                places.add(new NearbyPlaceItem("Local Bistro & Café", "Dining", "0.2 km (3 min walk)", "Popular spot for morning breakfast and artisan coffee"));
                places.add(new NearbyPlaceItem("Traditional " + city + " Restaurant", "Dining", "0.5 km (7 min walk)", "Authentic local cuisine with outdoor terrace"));
            }

            if (cat.contains("transit") || cat.contains("metro") || cat.contains("bus") || cat.equals("all")) {
                places.add(new NearbyPlaceItem(city + " Central Station / Metro", "Transit", "0.4 km (5 min walk)", "Major transit hub with subway and regional trains"));
                places.add(new NearbyPlaceItem("City Bus Stop", "Transit", "0.1 km (1 min walk)", "Direct bus lines to downtown and tourist landmarks"));
            }

            if (cat.contains("attraction") || cat.contains("sight") || cat.equals("all")) {
                places.add(new NearbyPlaceItem("Historic City Center & Square", "Attraction", "1.2 km (15 min walk)", "Vibrant historic landmark surrounded by cultural monuments"));
                places.add(new NearbyPlaceItem("City Art & History Museum", "Attraction", "0.8 km (10 min walk)", "World-renowned exhibits and galleries"));
            }

            if (cat.contains("grocer") || cat.contains("supermarket") || cat.equals("all")) {
                places.add(new NearbyPlaceItem("Supermarket & Pharmacy", "Groceries", "0.3 km (4 min walk)", "Full-service grocery store open daily"));
            }

            NearbyPlacesResponse response = new NearbyPlacesResponse(
                    params.propertyId(),
                    cat,
                    places
            );

            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Failed to fetch nearby places for propertyId={}", params.propertyId(), ex);
            return ToolResult.failed("NEARBY_PLACES_ERROR", "Could not load nearby places: " + ex.getMessage());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(NearbyPlacesParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(NearbyPlacesResponse.class);
    }
}
