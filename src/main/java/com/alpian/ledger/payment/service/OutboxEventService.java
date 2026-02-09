package com.alpian.ledger.payment.service;

import com.alpian.ledger.payment.domain.EventStatus;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.infrastructure.persistence.OutboxEventEntity;
import com.alpian.ledger.payment.infrastructure.persistence.OutboxEventRepository;
import com.alpian.ledger.payment.service.dto.PaymentCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    private static final String PAYMENT_TOPIC = "payment-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    @Timed(value = "outbox.publish", description = "Time taken to publish payment event to outbox")
    @Counted(value = "outbox.publish.count", description = "Number of events published to outbox")
    public void publishPaymentEvent(Payment payment) {
        PaymentCompletedEvent event = createPaymentCompletedEvent(payment);
        String eventPayload = serializeEvent(event);
        String partitionKey = determinePartitionKey(payment);

        OutboxEventEntity outboxEvent = createOutboxEvent(payment, partitionKey, eventPayload);
        outboxEventRepository.save(outboxEvent);


        log.info("Payment event for {} ({}) published to outbox with partition key {}",
                 payment.getPaymentId(), payment.getType(), partitionKey);
    }

    private PaymentCompletedEvent createPaymentCompletedEvent(Payment payment) {
        return new PaymentCompletedEvent(
                payment.getPaymentId(),
                payment.getType(),
                payment.getFromAccountId(),
                payment.getToAccountId(),
                payment.getAmount(),
                payment.getStatus(),
                Instant.now()
        );
    }

    private String determinePartitionKey(Payment payment) {
        return switch (payment.getType()) {
            case DEBIT, INTERNAL_TRANSFER -> payment.getFromAccountId();
            case CREDIT -> payment.getToAccountId();
        };
    }

    private OutboxEventEntity createOutboxEvent(Payment payment, String partitionKey, String eventPayload) {
        return new OutboxEventEntity(
                payment.getPaymentId(),
                partitionKey,
                "PaymentCompleted",
                eventPayload
        );
    }

    @Transactional
    @Timed(value = "outbox.process", description = "Time taken to process outbox events and publish to Kafka")
    public void processOutboxEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(EventStatus.NEW);
        if (pendingEvents.isEmpty()) {
            return;
        }
        log.info("Processing {} pending outbox events", pendingEvents.size());
        for (OutboxEventEntity event : pendingEvents) {
            try {
                kafkaTemplate.send(PAYMENT_TOPIC, event.getPartitionKey(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                markEventAsSent(event.getEventId());
                                log.info("Event {} published successfully to Kafka", event.getEventId());
                            } else {
                                markEventAsFailed(event.getEventId(), ex.getMessage());
                                log.error("Failed to publish event {} to Kafka: {}",
                                         event.getEventId(), ex.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.error("Error processing outbox event {}: {}", event.getEventId(), e.getMessage());
                markEventAsFailed(event.getEventId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void markEventAsSent(String eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(EventStatus.SENT);
            outboxEventRepository.save(event);
        });
    }

    @Transactional
    public void markEventAsFailed(String eventId, String errorMessage) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(EventStatus.FAILED);
            outboxEventRepository.save(event);
            log.error("Event {} marked as FAILED: {}", eventId, errorMessage);
        });
    }

    private String serializeEvent(PaymentCompletedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payment event", e);
            throw new IllegalStateException("Failed to serialize payment event", e);
        }
    }
}
