package com.luna.aggarly.booking.service.impl;

import com.luna.aggarly.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingExpirationJobTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingExpirationJob expirationJob;

    @Test
    void cleanupUnconfirmedBookings_CallsServiceWithTimeout() {
        ReflectionTestUtils.setField(expirationJob, "timeoutMinutes", 15);

        expirationJob.cleanupUnconfirmedBookings();

        verify(bookingService).expireUnconfirmedBookings(15);
    }
}
