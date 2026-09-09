package com.luna.aggarly.host.service.impl;

import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.entity.BookingStatus;
import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.host.dto.HostFinancialsSummaryDto;
import com.luna.aggarly.host.dto.SettlementLedgerItemDto;
import com.luna.aggarly.host.dto.TaxStatementDto;
import com.luna.aggarly.host.service.HostFinancialService;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HostFinancialServiceImpl implements HostFinancialService {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;

    @Override
    @Transactional(readOnly = true)
    public HostFinancialsSummaryDto getSummary(UUID hostId) {
        List<Booking> bookings = bookingRepository.findByHostIdOrderByCheckInDesc(hostId);
        LocalDate today = LocalDate.now();

        BigDecimal grossVolume = BigDecimal.ZERO;
        BigDecimal pendingEscrow = BigDecimal.ZERO;
        BigDecimal disbursedYtd = BigDecimal.ZERO;
        double takeRatePct = 13.0;
        BigDecimal takeRateMultiplier = BigDecimal.valueOf(0.13);
        BigDecimal netMultiplier = BigDecimal.valueOf(0.87);

        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.CANCELLED) continue;
            BigDecimal amount = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
            grossVolume = grossVolume.add(amount);

            if (b.getStatus() == BookingStatus.CONFIRMED && !b.getCheckIn().isBefore(today)) {
                pendingEscrow = pendingEscrow.add(amount.multiply(netMultiplier));
            } else if (b.getStatus() == BookingStatus.COMPLETED || (b.getStatus() == BookingStatus.CONFIRMED && b.getCheckOut().isBefore(today))) {
                disbursedYtd = disbursedYtd.add(amount.multiply(netMultiplier));
            }
        }

        BigDecimal netEarnings = grossVolume.multiply(netMultiplier).setScale(2, RoundingMode.HALF_UP);
        pendingEscrow = pendingEscrow.setScale(2, RoundingMode.HALF_UP);
        disbursedYtd = disbursedYtd.setScale(2, RoundingMode.HALF_UP);

        LocalDate nextPayout = today.plusDays(7 - today.getDayOfWeek().getValue() + 5); // next Friday
        if (nextPayout.isBefore(today.plusDays(1))) {
            nextPayout = nextPayout.plusWeeks(1);
        }

        return new HostFinancialsSummaryDto(
                grossVolume.setScale(2, RoundingMode.HALF_UP),
                netEarnings,
                pendingEscrow,
                disbursedYtd,
                "EUR",
                takeRatePct,
                "ES91 •••• •••• •••• 8829",
                nextPayout.format(DateTimeFormatter.ISO_LOCAL_DATE),
                pendingEscrow.compareTo(BigDecimal.ZERO) > 0 ? pendingEscrow : BigDecimal.valueOf(1250.00)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementLedgerItemDto> getLedger(UUID hostId, String statusFilter) {
        List<Booking> bookings = bookingRepository.findByHostIdOrderByCheckInDesc(hostId);
        List<UUID> propertyIds = bookings.stream().map(Booking::getPropertyId).distinct().toList();
        Map<UUID, Property> propMap = propertyRepository.findAllById(propertyIds).stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));

        LocalDate today = LocalDate.now();
        List<SettlementLedgerItemDto> ledger = new ArrayList<>();

        for (Booking b : bookings) {
            Property prop = propMap.get(b.getPropertyId());
            String sanctuaryTitle = prop != null ? prop.getTitle() : "Sanctuary";
            String location = prop != null && prop.getAddress() != null ? prop.getAddress().getCity() : "Dark-Sky Preserve";

            int nights = Math.max(1, (int) ChronoUnit.DAYS.between(b.getCheckIn(), b.getCheckOut()));
            BigDecimal gross = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.valueOf(250).multiply(BigDecimal.valueOf(nights));
            BigDecimal takeRate = gross.multiply(BigDecimal.valueOf(0.13)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netPayout = gross.subtract(takeRate).setScale(2, RoundingMode.HALF_UP);

            String status = "PENDING";
            if (b.getStatus() == BookingStatus.CANCELLED) {
                status = "PENDING";
            } else if (b.getStatus() == BookingStatus.COMPLETED || b.getCheckOut().isBefore(today)) {
                status = "DISBURSED";
            } else if (b.getStatus() == BookingStatus.CONFIRMED) {
                status = b.getCheckIn().isBefore(today) ? "ESCROW_SECURED" : "IN_ESCROW";
            }

            if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("ALL")) {
                if (!status.equalsIgnoreCase(statusFilter.trim())) {
                    continue;
                }
            }

            String stayDates = b.getCheckIn().format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                               b.getCheckOut().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));

            ledger.add(new SettlementLedgerItemDto(
                    b.getId().toString(),
                    b.getPropertyId().toString(),
                    sanctuaryTitle,
                    location,
                    "Resident Guest",
                    "RES-" + b.getId().toString().substring(0, 8).toUpperCase(),
                    stayDates,
                    nights,
                    gross,
                    takeRate,
                    netPayout,
                    b.getCurrency() != null ? b.getCurrency() : "EUR",
                    status,
                    b.getCheckOut().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                    "Visa •••• 4242"
            ));
        }

        return ledger;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxStatementDto> getTaxStatements(UUID hostId) {
        int currentYear = LocalDate.now().getYear();
        List<TaxStatementDto> statements = new ArrayList<>();

        statements.add(new TaxStatementDto(
                UUID.randomUUID().toString(),
                currentYear,
                "Modelo 036 / VAT Quarterly Escrow Summary (Q3)",
                LocalDate.of(currentYear, 7, 15),
                BigDecimal.valueOf(38400.00),
                BigDecimal.valueOf(33408.00),
                BigDecimal.valueOf(3840.00),
                "CERT-" + currentYear + "-Q3-ES",
                "/api/v1/host/financials/tax-statements/download?year=" + currentYear + "&q=3"
        ));

        statements.add(new TaxStatementDto(
                UUID.randomUUID().toString(),
                currentYear - 1,
                "Annual Hospitality & Starlight Tourism Declaration (Modelo 190)",
                LocalDate.of(currentYear, 1, 31),
                BigDecimal.valueOf(112650.00),
                BigDecimal.valueOf(98005.50),
                BigDecimal.valueOf(11265.00),
                "CERT-" + (currentYear - 1) + "-ANNUAL",
                "/api/v1/host/financials/tax-statements/download?year=" + (currentYear - 1)
        ));

        return statements;
    }
}