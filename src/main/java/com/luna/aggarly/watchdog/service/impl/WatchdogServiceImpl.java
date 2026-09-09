package com.luna.aggarly.watchdog.service.impl;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.watchdog.dto.CreateWatchdogRequest;
import com.luna.aggarly.watchdog.dto.WatchdogAlertDto;
import com.luna.aggarly.watchdog.entity.UserWatchdog;
import com.luna.aggarly.watchdog.repository.UserWatchdogRepository;
import com.luna.aggarly.watchdog.service.WatchdogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchdogServiceImpl implements WatchdogService {

    private final UserWatchdogRepository watchdogRepository;
    private final PropertyRepository propertyRepository;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional(readOnly = true)
    public List<WatchdogAlertDto> getWatchdogs(UUID userId) {
        return watchdogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public WatchdogAlertDto createWatchdog(CreateWatchdogRequest req, UUID userId) {
        String title = req.sanctuaryTitle();
        String location = req.sanctuaryLocation();
        BigDecimal currentPrice = req.originalPrice();

        if (req.propertyId() != null) {
            Property prop = propertyRepository.findById(req.propertyId()).orElse(null);
            if (prop != null) {
                if (title == null || title.isBlank()) title = prop.getTitle();
                if (location == null || location.isBlank()) {
                    location = prop.getAddress() != null ? prop.getAddress().getCity() + ", " + prop.getAddress().getCountry() : "Dark-Sky Preserve";
                }
                if (currentPrice == null) currentPrice = prop.getBasePricePerNight();
            }
        }

        BigDecimal target = req.targetPrice() != null ? req.targetPrice() : req.targetNightlyCeiling();
        if (target == null) target = BigDecimal.valueOf(350);
        if (currentPrice == null) currentPrice = BigDecimal.valueOf(450);

        UserWatchdog watchdog = UserWatchdog.builder()
                .userId(userId)
                .propertyId(req.propertyId())
                .sanctuaryTitle(title != null ? title : "Observatory Sanctuary")
                .sanctuaryLocation(location != null ? location : "Dark-Sky Preserve")
                .bortleRating(req.bortleRating() != null ? req.bortleRating() : "BORTLE 2")
                .targetDates(req.targetDates() != null ? req.targetDates() : "New Moon Window")
                .originalPrice(currentPrice)
                .currentPrice(currentPrice)
                .targetPrice(target)
                .status("ACTIVE")
                .notificationsChannel(req.notificationsChannel() != null ? req.notificationsChannel() : "SMS & Push")
                .solsticeTrigger(Boolean.TRUE.equals(req.solsticeTrigger()))
                .isActive(true)
                .build();

        watchdog = watchdogRepository.save(watchdog);
        return mapToDto(watchdog);
    }

    @Override
    @Transactional
    public boolean toggleWatchdog(UUID watchdogId, boolean active, UUID userId) {
        UserWatchdog watchdog = watchdogRepository.findByIdAndUserId(watchdogId, userId).orElse(null);
        if (watchdog == null) return false;

        watchdog.setIsActive(active);
        watchdog.setStatus(active ? "ACTIVE" : "PAUSED");
        watchdogRepository.save(watchdog);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteWatchdog(UUID watchdogId, UUID userId) {
        UserWatchdog watchdog = watchdogRepository.findByIdAndUserId(watchdogId, userId).orElse(null);
        if (watchdog == null) return false;

        watchdogRepository.delete(watchdog);
        return true;
    }

    @Override
    @Transactional
    public void evaluateWatchdogs() {
        List<UserWatchdog> activeWatchdogs = watchdogRepository.findByIsActiveTrue();
        for (UserWatchdog w : activeWatchdogs) {
            if (w.getPropertyId() == null) continue;
            Property p = propertyRepository.findById(w.getPropertyId()).orElse(null);
            if (p != null && p.getBasePricePerNight() != null) {
                w.setCurrentPrice(p.getBasePricePerNight());
                if (p.getBasePricePerNight().compareTo(w.getTargetPrice()) <= 0) {
                    w.setStatus("TRIGGERED");
                    notificationDispatcher.dispatch(
                            w.getUserId(),
                            NotificationCategory.ALERTS,
                            "WATCHDOG_TARGET_MET",
                            "Price Target Met: " + w.getSanctuaryTitle(),
                            "Nightly rate dropped to €" + p.getBasePricePerNight() + " (target €" + w.getTargetPrice() + ")",
                            "{\"watchdogId\":\"" + w.getId() + "\",\"propertyId\":\"" + w.getPropertyId() + "\"}",
                            null,
                            null
                    );
                }
                watchdogRepository.save(w);
            }
        }
    }

    private WatchdogAlertDto mapToDto(UserWatchdog w) {
        String img = "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80";
        return new WatchdogAlertDto(
                w.getId().toString(),
                w.getSanctuaryTitle(),
                w.getSanctuaryLocation(),
                w.getBortleRating() != null ? w.getBortleRating() : "BORTLE 2",
                w.getTargetDates() != null ? w.getTargetDates() : "New Moon Phase",
                w.getOriginalPrice() != null ? w.getOriginalPrice() : BigDecimal.valueOf(450),
                w.getTargetPrice() != null ? w.getTargetPrice() : BigDecimal.valueOf(350),
                w.getCurrentPrice() != null ? w.getCurrentPrice() : BigDecimal.valueOf(420),
                w.getStatus(),
                w.getCreatedAt() != null ? DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC).format(w.getCreatedAt()) : "Active",
                img,
                w.getSolsticeTrigger(),
                w.getNotificationsChannel() != null ? w.getNotificationsChannel() : "SMS & Push"
        );
    }
}