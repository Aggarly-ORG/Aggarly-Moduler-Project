package com.luna.aggarly.booking.mapper;

import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.BookingStatusHistoryResponse;
import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.entity.BookingStatusHistory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    default BookingResponse toResponse(Booking booking, String clientSecret) {
        if (booking == null) {
            return null;
        }
        return new BookingResponse(
                booking.getId(),
                booking.getPropertyId(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getGuestCount(),
                booking.getStatus() != null ? booking.getStatus().name() : null,
                booking.getTotalAmount(),
                booking.getCurrency(),
                clientSecret
        );
    }

    default BookingStatusHistoryResponse toHistoryResponse(BookingStatusHistory history) {
        if (history == null) {
            return null;
        }
        return new BookingStatusHistoryResponse(
                history.getFromStatus() != null ? history.getFromStatus().name() : null,
                history.getToStatus() != null ? history.getToStatus().name() : null,
                history.getReason(),
                history.getCreatedAt()
        );
    }
}
