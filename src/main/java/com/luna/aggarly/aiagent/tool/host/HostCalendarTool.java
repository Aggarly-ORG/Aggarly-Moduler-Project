package com.luna.aggarly.aiagent.tool.host;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.availability.dto.BlockDatesRequest;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HostCalendarTool implements Tool<HostCalendarTool.Params, String> {

    public record Params(
            UUID propertyId,
            LocalDate startDate,
            LocalDate endDate,
            String reason
    ) {}

    private final AvailabilityService availabilityService;

    @Override
    public String name() {
        return "host.calendarBlock";
    }

    @Override
    public String description() {
        return "Block dates on a property's calendar for maintenance or personal use.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(Params params, UserPrincipal user) {
        availabilityService.blockDates(params.propertyId(), new BlockDatesRequest(params.startDate(), params.endDate(), params.reason()));
        return ToolResult.ok("Dates successfully blocked from " + params.startDate() + " to " + params.endDate());
    }
}
