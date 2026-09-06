package com.luna.aggarly.common.expression.library;

import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import com.luna.aggarly.pricing.repository.CouponRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ExpressionFunctionLibrary(value = "entity", prefix = "EntityUtils")
public class EntityFunctionLibrary {

    private final PropertyRepository propertyRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final AvailabilityService availabilityService;

    public EntityFunctionLibrary(
            PropertyRepository propertyRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            CouponRepository couponRepository,
            @Autowired(required = false) @Lazy AvailabilityService availabilityService
    ) {
        this.propertyRepository = propertyRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.couponRepository = couponRepository;
        this.availabilityService = availabilityService;
    }

    @ExpressionFunction(value = "property", description = "Loads full property entity by UUID")
    public Object property(String propertyId) {
        if (propertyId == null || propertyId.isBlank()) return null;
        try {
            UUID id = UUID.fromString(propertyId.trim());
            return propertyRepository.findById(id).orElse(null);
        } catch (Exception ex) {
            log.debug("property lookup error for '{}': {}", propertyId, ex.getMessage());
            return null;
        }
    }

    @ExpressionFunction(value = "booking", description = "Loads full booking entity by UUID")
    public Object booking(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) return null;
        try {
            UUID id = UUID.fromString(bookingId.trim());
            return bookingRepository.findById(id).orElse(null);
        } catch (Exception ex) {
            log.debug("booking lookup error for '{}': {}", bookingId, ex.getMessage());
            return null;
        }
    }

    @ExpressionFunction(value = "user", description = "Loads user entity by UUID")
    public Object user(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            UUID id = UUID.fromString(userId.trim());
            return userRepository.findById(id).orElse(null);
        } catch (Exception ex) {
            log.debug("user lookup error for '{}': {}", userId, ex.getMessage());
            return null;
        }
    }

    @ExpressionFunction(value = "coupon", description = "Loads coupon entity by code")
    public Object coupon(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return couponRepository.findByCodeAndActiveTrue(code.trim()).orElse(null);
        } catch (Exception ex) {
            log.debug("coupon lookup error for '{}': {}", code, ex.getMessage());
            return null;
        }
    }

    @ExpressionFunction(value = "availability", aliases = {"availablity", "check_availability", "calendar"}, description = "Checks property availability between check-in and check-out dates")
    public Object availability(String arg1, String arg2, String arg3) {
        try {
            if (arg1 == null || arg1.isBlank()) return null;

            // Check if 2 args passed (checkIn, checkOut)
            if (arg3 == null || arg3.isBlank()) {
                String checkIn = arg1.trim();
                String checkOut = (arg2 != null && !arg2.isBlank()) ? arg2.trim() : checkIn;
                return Map.of(
                        "checkIn", checkIn,
                        "checkOut", checkOut,
                        "available", true
                );
            }

            // 3 args passed (propertyId, checkIn, checkOut)
            UUID propertyId = UUID.fromString(arg1.trim());
            LocalDate in = LocalDate.parse(arg2.trim());
            LocalDate out = LocalDate.parse(arg3.trim());

            if (availabilityService != null) {
                return availabilityService.checkAvailability(propertyId, in, out);
            }

            return Map.of(
                    "propertyId", propertyId.toString(),
                    "checkIn", in.toString(),
                    "checkOut", out.toString(),
                    "available", true
            );
        } catch (Exception ex) {
            log.debug("availability check error: {}", ex.getMessage());
            return Map.of("available", false, "error", ex.getMessage());
        }
    }
}
