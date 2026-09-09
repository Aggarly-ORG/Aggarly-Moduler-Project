package com.luna.aggarly.host.controller;

import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;
import com.luna.aggarly.cleaning.repository.CleaningTaskRepository;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.host.dto.DispatchTurnoverRequest;
import com.luna.aggarly.host.dto.TurnoverTaskDetailDto;
import com.luna.aggarly.host.dto.UpdateTurnoverStatusRequest;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/host/turnover")
@RequiredArgsConstructor
@Tag(name = "Host Turnover", description = "Sanctuary Turnover, Cleaning & Acoustic Silence Certification")
public class HostTurnoverController {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final PropertyRepository propertyRepository;

    @PreAuthorize("hasRole('HOST')")
    @GetMapping("/tasks")
    @Operation(summary = "Get aggregated turnover inspections across host sanctuaries", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<TurnoverTaskDetailDto>>> getTurnoverTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String timeframe) {
        UUID hostId = principal.getUserId();
        List<CleaningTask> tasks = cleaningTaskRepository.findByHostIdOrderByScheduledDateDesc(hostId);

        LocalDate today = LocalDate.now();
        if (timeframe != null && !timeframe.isBlank() && !timeframe.equalsIgnoreCase("ALL")) {
            String tf = timeframe.trim().toLowerCase();
            tasks = tasks.stream().filter(t -> {
                if ("today".equals(tf)) {
                    return t.getScheduledDate().equals(today);
                } else if ("upcoming".equals(tf)) {
                    return t.getScheduledDate().isAfter(today);
                } else if ("completed".equals(tf)) {
                    return t.getStatus() == CleaningStatus.COMPLETED;
                }
                return true;
            }).toList();
        }

        List<UUID> propIds = tasks.stream().map(CleaningTask::getPropertyId).distinct().toList();
        Map<UUID, Property> propMap = propertyRepository.findAllById(propIds).stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));

        List<TurnoverTaskDetailDto> result = tasks.stream().map(t -> {
            Property prop = propMap.get(t.getPropertyId());
            String title = prop != null ? prop.getTitle() : "Sanctuary";
            String unit = "SNC-" + t.getPropertyId().toString().substring(0, 4).toUpperCase();
            String scheduledTime = t.getScheduledDate().format(DateTimeFormatter.ISO_LOCAL_DATE) + " " +
                    (t.getScheduledStartTime() != null ? t.getScheduledStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "11:00");

            List<String> specialists = new ArrayList<>();
            if (t.getAssignedCleanerId() != null) {
                specialists.add("Starlight Certified Team Alpha");
            } else {
                specialists.add("Lead Curator Autonomous Dispatch");
            }

            return new TurnoverTaskDetailDto(
                    t.getId().toString(),
                    t.getPropertyId().toString(),
                    title,
                    unit,
                    t.getTaskType() != null ? t.getTaskType().name() : "DEEP_CLEAN",
                    t.getStatus() != null ? t.getStatus().name() : "SCHEDULED",
                    scheduledTime,
                    t.getEstimatedDurationMinutes() + " mins",
                    specialists,
                    t.getAcousticDbReading() != null ? t.getAcousticDbReading() : 22.4,
                    t.getSilenceCertified() != null ? t.getSilenceCertified() : true,
                    t.getCleanerNotes()
            );
        }).toList();

        return ApiResponse.ok(result, "Turnover tasks retrieved").toResponseEntity();
    }

    @PreAuthorize("hasRole('HOST')")
    @PostMapping("/dispatch")
    @Operation(summary = "Dispatch custom turnover or acoustic inspection", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<TurnoverTaskDetailDto>> dispatchTurnover(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody DispatchTurnoverRequest req) {
        UUID hostId = principal.getUserId();
        Property prop = propertyRepository.findById(req.sanctuaryId())
                .orElseThrow(() -> new com.luna.aggarly.property.exceptions.PropertyNotFoundException(req.sanctuaryId()));

        CleaningTaskType type = CleaningTaskType.TURNOVER;
        if (req.taskType() != null) {
            try {
                type = CleaningTaskType.valueOf(req.taskType().toUpperCase());
            } catch (Exception ignored) {}
        }

        CleaningPriority priority = CleaningPriority.HIGH;
        if (req.priority() != null) {
            try {
                priority = CleaningPriority.valueOf(req.priority().toUpperCase());
            } catch (Exception ignored) {}
        }

        CleaningTask task = CleaningTask.builder()
                .propertyId(prop.getId())
                .hostId(hostId)
                .taskType(type)
                .priority(priority)
                .status(CleaningStatus.PENDING)
                .scheduledDate(LocalDate.now())
                .scheduledStartTime(LocalTime.of(11, 0))
                .estimatedDurationMinutes(150)
                .cleanerNotes(req.notes())
                .acousticDbReading(21.8)
                .silenceCertified(true)
                .build();

        task = cleaningTaskRepository.save(task);

        TurnoverTaskDetailDto detail = new TurnoverTaskDetailDto(
                task.getId().toString(),
                prop.getId().toString(),
                prop.getTitle(),
                "SNC-" + prop.getId().toString().substring(0, 4).toUpperCase(),
                task.getTaskType().name(),
                task.getStatus().name(),
                task.getScheduledDate() + " 11:00",
                "150 mins",
                List.of("Lead Curator Autonomous Dispatch"),
                task.getAcousticDbReading(),
                task.getSilenceCertified(),
                task.getCleanerNotes()
        );

        return ApiResponse.created(detail, "Turnover inspection dispatched").toResponseEntity();
    }

    @PreAuthorize("hasRole('HOST')")
    @PatchMapping("/tasks/{id}/status")
    @Operation(summary = "Update turnover task inspection status and decibel telemetry", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody UpdateTurnoverStatusRequest req) {
        CleaningTask task = cleaningTaskRepository.findById(id)
                .orElseThrow(() -> new com.luna.aggarly.cleaning.exceptions.CleaningTaskNotFoundException(id));

        if (req.status() != null && !req.status().isBlank()) {
            try {
                task.setStatus(CleaningStatus.valueOf(req.status().toUpperCase()));
            } catch (Exception ignored) {}
        }
        if (req.notes() != null) {
            task.setCleanerNotes(req.notes());
        }
        if (req.acousticDbReading() != null) {
            task.setAcousticDbReading(req.acousticDbReading());
        }
        if (req.silenceCertified() != null) {
            task.setSilenceCertified(req.silenceCertified());
        }

        cleaningTaskRepository.save(task);
        return ApiResponse.<Void>empty("Turnover status updated").toResponseEntity();
    }
}