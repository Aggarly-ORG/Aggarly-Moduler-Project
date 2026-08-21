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
public class UserHistoryTool implements Tool<UUID, List<BookingResponse>> {

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
    public Class<UUID> parameterType() {
        return UUID.class;
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
    public ToolResult<List<BookingResponse>> execute(UUID userId, UserPrincipal user) {
        UUID targetUserId = userId != null ? userId : (user != null ? user.getUserId() : null);
        if (targetUserId == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to view booking history.");
        }
        List<BookingResponse> bookings = bookingService.getMyBookings(targetUserId);
        return ToolResult.ok(bookings);
    }
}
