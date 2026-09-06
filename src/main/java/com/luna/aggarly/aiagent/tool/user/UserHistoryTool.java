package com.luna.aggarly.aiagent.tool.user;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserHistoryTool implements Tool<UserHistoryTool.Params, List<BookingResponse>> {

    public record Params(
            UUID userId
    ) {}

    private final BookingService bookingService;

    @Override
    public String name() {
        return "user.history";
    }

    @Override
    public String description() {
        return "Fetch past stay and booking history for a user.";
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
    public ToolResult<List<BookingResponse>> execute(Params params, UserPrincipal user) {
        UUID targetUserId = (params != null && params.userId() != null)
                ? params.userId()
                : (user != null ? user.getUserId() : null);
        if (targetUserId == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to view booking history.");
        }
        List<BookingResponse> bookings = bookingService.getMyBookings(targetUserId);
        return ToolResult.ok(bookings);
    }
}
