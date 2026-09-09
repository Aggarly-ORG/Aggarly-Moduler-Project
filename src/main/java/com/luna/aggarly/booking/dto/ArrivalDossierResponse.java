package com.luna.aggarly.booking.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ArrivalDossierResponse(
        UUID bookingId,
        UUID propertyId,
        String sanctuaryTitle,
        String guestName,
        LocalDate checkInDate,
        String checkInWindow,
        LocalDate checkOutDate,
        String checkOutWindow,
        String vaultPin,
        String wifiSsid,
        String wifiPasskey,
        Double latitude,
        Double longitude,
        String arrivalCadenceInstructions,
        String parkingProtocols,
        String telescopeCalibrationStatus
) {}