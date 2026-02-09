package com.alpian.ledger.payment.service;

import com.alpian.ledger.payment.domain.EventStatus;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.domain.TransactionType;
import com.alpian.ledger.payment.infrastructure.persistence.OutboxEventEntity;
import com.alpian.ledger.payment.infrastructure.persistence.OutboxEventRepository;
import com.alpian.ledger.payment.service.dto.PaymentCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventService outboxEventService;

    @Captor
    private ArgumentCaptor<OutboxEventEntity> outboxEventCaptor;

    private Payment debitPayment;
    private Payment creditPayment;
    private Payment transferPayment;

    @BeforeEach
    void setUp() {
        debitPayment = new Payment(
                "PAY-001",
                TransactionType.DEBIT,
                "ACC-001",
                null,
                new BigDecimal("100.00"),
                "key-001"
        );
        debitPayment.complete();

        creditPayment = new Payment(
                "PAY-002",
                TransactionType.CREDIT,
                null,
                "ACC-002",
                new BigDecimal("50.00"),
                "key-002"
        );
        creditPayment.complete();

        transferPayment = new Payment(
                "PAY-003",
                TransactionType.INTERNAL_TRANSFER,
                "ACC-001",
                "ACC-002",
                new BigDecimal("75.00"),
                "key-003"
        );
        transferPayment.complete();
    }

    @Test
    void shouldPublishDebitPaymentEventToOutboxWhenCalled() throws JsonProcessingException {
        // Given
        String serializedEvent = "{\"paymentId\":\"PAY-001\"}";
        when(objectMapper.writeValueAsString(any(PaymentCompletedEvent.class)))
                .thenReturn(serializedEvent);

        // When
        outboxEventService.publishPaymentEvent(debitPayment);

        // Then
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEventEntity savedEvent = outboxEventCaptor.getValue();

        assertThat(savedEvent.getAggregateId()).isEqualTo("PAY-001");
        assertThat(savedEvent.getPartitionKey()).isEqualTo("ACC-001"); // DEBIT uses fromAccountId
        assertThat(savedEvent.getType()).isEqualTo("PaymentCompleted");
        assertThat(savedEvent.getPayload()).isEqualTo(serializedEvent);
        assertThat(savedEvent.getStatus()).isEqualTo(EventStatus.NEW);

        verify(objectMapper).writeValueAsString(any(PaymentCompletedEvent.class));
    }

    @Test
    void shouldPublishCreditPaymentEventWithToAccountAsPartitionKeyWhenCalled() throws JsonProcessingException {
        // Given
        String serializedEvent = "{\"paymentId\":\"PAY-002\"}";
        when(objectMapper.writeValueAsString(any(PaymentCompletedEvent.class)))
                .thenReturn(serializedEvent);

        // When
        outboxEventService.publishPaymentEvent(creditPayment);

        // Then
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEventEntity savedEvent = outboxEventCaptor.getValue();

        assertThat(savedEvent.getAggregateId()).isEqualTo("PAY-002");
        assertThat(savedEvent.getPartitionKey()).isEqualTo("ACC-002"); // CREDIT uses toAccountId
        assertThat(savedEvent.getType()).isEqualTo("PaymentCompleted");
        assertThat(savedEvent.getPayload()).isEqualTo(serializedEvent);
    }

    @Test
    void shouldPublishInternalTransferEventWithFromAccountAsPartitionKeyWhenCalled() throws JsonProcessingException {
        // Given
        String serializedEvent = "{\"paymentId\":\"PAY-003\"}";
        when(objectMapper.writeValueAsString(any(PaymentCompletedEvent.class)))
                .thenReturn(serializedEvent);

        // When
        outboxEventService.publishPaymentEvent(transferPayment);

        // Then
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEventEntity savedEvent = outboxEventCaptor.getValue();

        assertThat(savedEvent.getAggregateId()).isEqualTo("PAY-003");
        assertThat(savedEvent.getPartitionKey()).isEqualTo("ACC-001"); // INTERNAL_TRANSFER uses fromAccountId
        assertThat(savedEvent.getType()).isEqualTo("PaymentCompleted");
    }

    @Test
    void shouldThrowExceptionWhenSerializationFails() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(any(PaymentCompletedEvent.class)))
                .thenThrow(new JsonProcessingException("Serialization error") {});

        // When/Then
        assertThatThrownBy(() -> outboxEventService.publishPaymentEvent(debitPayment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize payment event");

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldProcessPendingOutboxEventsWhenEventsExist() {
        // Given
        OutboxEventEntity event1 = createOutboxEvent("EVENT-001", "ACC-001", EventStatus.NEW);
        OutboxEventEntity event2 = createOutboxEvent("EVENT-002", "ACC-002", EventStatus.NEW);
        List<OutboxEventEntity> pendingEvents = Arrays.asList(event1, event2);

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(EventStatus.NEW))
                .thenReturn(pendingEvents);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("payment-events"), anyString(), anyString()))
                .thenReturn(future);

        // When
        outboxEventService.processOutboxEvents();

        // Then
        verify(kafkaTemplate, times(2)).send(eq("payment-events"), anyString(), anyString());
        verify(kafkaTemplate).send("payment-events", "ACC-001", event1.getPayload());
        verify(kafkaTemplate).send("payment-events", "ACC-002", event2.getPayload());
    }

    @Test
    void shouldNotProcessEventsWhenNoPendingEvents() {
        // Given
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(EventStatus.NEW))
                .thenReturn(Collections.emptyList());

        // When
        outboxEventService.processOutboxEvents();

        // Then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldMarkEventAsSentWhenKafkaPublishSucceeds() {
        // Given
        String eventId = "EVENT-001";
        OutboxEventEntity event = createOutboxEvent(eventId, "ACC-001", EventStatus.NEW);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // When
        outboxEventService.markEventAsSent(eventId);

        // Then
        verify(outboxEventRepository).findById(eventId);
        verify(outboxEventRepository).save(event);
        assertThat(event.getStatus()).isEqualTo(EventStatus.SENT);
    }

    @Test
    void shouldNotSaveWhenMarkingNonExistentEventAsSent() {
        // Given
        String eventId = "NON-EXISTENT";
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.empty());

        // When
        outboxEventService.markEventAsSent(eventId);

        // Then
        verify(outboxEventRepository).findById(eventId);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldMarkEventAsFailedWhenKafkaPublishFails() {
        // Given
        String eventId = "EVENT-001";
        String errorMessage = "Kafka connection timeout";
        OutboxEventEntity event = createOutboxEvent(eventId, "ACC-001", EventStatus.NEW);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // When
        outboxEventService.markEventAsFailed(eventId, errorMessage);

        // Then
        verify(outboxEventRepository).findById(eventId);
        verify(outboxEventRepository).save(event);
        assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
    }

    @Test
    void shouldNotSaveWhenMarkingNonExistentEventAsFailed() {
        // Given
        String eventId = "NON-EXISTENT";
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.empty());

        // When
        outboxEventService.markEventAsFailed(eventId, "Some error");

        // Then
        verify(outboxEventRepository).findById(eventId);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldSendEventWithCorrectPartitionKeyForDebitTransaction() throws JsonProcessingException {
        // Given
        String serializedEvent = "{\"paymentId\":\"PAY-001\"}";
        when(objectMapper.writeValueAsString(any(PaymentCompletedEvent.class)))
                .thenReturn(serializedEvent);

        // When
        outboxEventService.publishPaymentEvent(debitPayment);

        // Then
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEventEntity savedEvent = outboxEventCaptor.getValue();

        // For DEBIT, partition key should be fromAccountId (sender)
        assertThat(savedEvent.getPartitionKey()).isEqualTo(debitPayment.getFromAccountId());
    }

    @Test
    void shouldSendEventWithCorrectPartitionKeyForCreditTransaction() throws JsonProcessingException {
        // Given
        String serializedEvent = "{\"paymentId\":\"PAY-002\"}";
        when(objectMapper.writeValueAsString(any(PaymentCompletedEvent.class)))
                .thenReturn(serializedEvent);

        // When
        outboxEventService.publishPaymentEvent(creditPayment);

        // Then
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEventEntity savedEvent = outboxEventCaptor.getValue();

        // For CREDIT, partition key should be toAccountId (receiver)
        assertThat(savedEvent.getPartitionKey()).isEqualTo(creditPayment.getToAccountId());
    }

    @Test
    void shouldSendEventWithCorrectPartitionKeyForInternalTransferTransaction() throws JsonProcessingException {
        // Given
        String serializedEvent = "{\"paymentId\":\"PAY-003\"}";
        when(objectMapper.writeValueAsString(any(PaymentCompletedEvent.class)))
                .thenReturn(serializedEvent);

        // When
        outboxEventService.publishPaymentEvent(transferPayment);

        // Then
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEventEntity savedEvent = outboxEventCaptor.getValue();

        // For INTERNAL_TRANSFER, partition key should be fromAccountId (sender) for ordering
        assertThat(savedEvent.getPartitionKey()).isEqualTo(transferPayment.getFromAccountId());
    }

    @Test
    void shouldCreatePaymentCompletedEventWithCorrectFieldsWhenPublishing() throws JsonProcessingException {
        // Given
        ArgumentCaptor<PaymentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        when(objectMapper.writeValueAsString(eventCaptor.capture()))
                .thenReturn("{\"paymentId\":\"PAY-001\"}");

        // When
        outboxEventService.publishPaymentEvent(debitPayment);

        // Then
        PaymentCompletedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getPaymentId()).isEqualTo("PAY-001");
        assertThat(capturedEvent.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(capturedEvent.getFromAccountId()).isEqualTo("ACC-001");
        assertThat(capturedEvent.getToAccountId()).isNull();
        assertThat(capturedEvent.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(capturedEvent.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(capturedEvent.getTimestamp()).isNotNull();
    }

    private OutboxEventEntity createOutboxEvent(String eventId, String partitionKey, EventStatus status) {
        OutboxEventEntity event = new OutboxEventEntity(
                "PAY-" + eventId,
                partitionKey,
                "PaymentCompleted",
                "{\"test\":\"data\"}"
        );
        event.setEventId(eventId);
        event.setStatus(status);
        return event;
    }
}

