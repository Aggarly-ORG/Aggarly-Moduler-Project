package com.luna.aggarly.scheduler.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.scheduler.engine.ScheduledEventProcessor;
import com.luna.aggarly.scheduler.entity.OutboxEvent;
import com.luna.aggarly.scheduler.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final ScheduledEventProcessor eventProcessor;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent recordEvent(String eventType, String aggregateType, String aggregateId, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent event = OutboxEvent.builder()
                    .eventType(eventType)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .payload(payloadJson)
                    .status("PENDING")
                    .build();
            return outboxEventRepository.save(event);
        } catch (Exception ex) {
            log.error("Failed to record outbox event for aggregateId={}", aggregateId, ex);
            throw new RuntimeException("Outbox persistence failed", ex);
        }
    }

    //@Scheduled(fixedDelay = 3000, initialDelay = 10000)
    @Transactional
    public void processOutboxBatch() {
        List<OutboxEvent> pending = outboxEventRepository.claimPendingEventsForPublishing(50);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox event(s)...", pending.size());
        for (OutboxEvent event : pending) {
            try {
                Object payloadObj = objectMapper.readValue(event.getPayload(), Map.class);
                eventProcessor.processEvent(event.getEventType(), payloadObj);

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                log.error("Failed to dispatch outbox event {}", event.getId(), ex);
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(ex.getMessage());
                if (event.getAttempts() >= 5) {
                    event.setStatus("DEAD_LETTER");
                }
            }
        }
        outboxEventRepository.saveAll(pending);
    }
}
