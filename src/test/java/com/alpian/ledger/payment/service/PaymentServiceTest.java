package com.alpian.ledger.payment.service;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.domain.TransactionType;
import com.alpian.ledger.payment.exception.IdempotencyConflictException;
import com.alpian.ledger.payment.infrastructure.mapper.PaymentMapper;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentEntity;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentRepository;
import com.alpian.ledger.payment.service.strategy.PaymentStrategy;
import com.alpian.ledger.payment.service.strategy.PaymentStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentStrategyFactory strategyFactory;

    @Mock
    private PaymentStrategy paymentStrategy;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private PaymentService paymentService;

    private CreatePaymentRequest request;
    private String idempotencyKey;
    private Payment mockPayment;

    @BeforeEach
    void setUp() {
        request = new CreatePaymentRequest(
                TransactionType.DEBIT,
                "ACC-001",
                null,
                new BigDecimal("100.00")
        );
        idempotencyKey = "test-idempotency-key";

        mockPayment = new Payment(
                "PAY-123",
                TransactionType.DEBIT,
                "ACC-001",
                null,
                new BigDecimal("100.00"),
                idempotencyKey
        );
        mockPayment.complete();
    }

    @Test
    void shouldCreatePaymentWhenValidRequest() {
        // Given
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(strategyFactory.getStrategy(TransactionType.DEBIT))
                .thenReturn(paymentStrategy);
        when(paymentStrategy.execute(request, idempotencyKey))
                .thenReturn(mockPayment);

        // When
        Payment result = paymentService.createPayment(request, idempotencyKey);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPaymentId()).isEqualTo("PAY-123");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(paymentRepository).findByIdempotencyKey(idempotencyKey);
        verify(strategyFactory).getStrategy(TransactionType.DEBIT);
        verify(paymentStrategy).execute(request, idempotencyKey);
        verify(outboxEventService).publishPaymentEvent(mockPayment);
    }

    @Test
    void shouldThrowIdempotencyConflictWhenKeyAlreadyExists() {
        // Given
        PaymentEntity existingPayment = new PaymentEntity();
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingPayment));

        // When/Then
        assertThatThrownBy(() -> paymentService.createPayment(request, idempotencyKey))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("idempotency key already exists");

        verify(paymentRepository).findByIdempotencyKey(idempotencyKey);
        verify(strategyFactory, never()).getStrategy(any());
        verify(outboxEventService, never()).publishPaymentEvent(any());
    }

    @Test
    void shouldPublishEventToOutboxWhenPaymentCreated() {
        // Given
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(strategyFactory.getStrategy(TransactionType.DEBIT))
                .thenReturn(paymentStrategy);
        when(paymentStrategy.execute(request, idempotencyKey))
                .thenReturn(mockPayment);

        // When
        paymentService.createPayment(request, idempotencyKey);

        // Then
        verify(outboxEventService).publishPaymentEvent(mockPayment);
    }

    @Test
    void shouldReturnPagedPaymentsWhenGetPaymentHistory() {
        // Given
        String accountId = "ACC-001";
        Pageable pageable = PageRequest.of(0, 20);

        PaymentEntity entity1 = new PaymentEntity();

        PaymentEntity entity2 = new PaymentEntity();

        Page<PaymentEntity> entityPage = new PageImpl<>(Arrays.asList(entity1, entity2));

        Payment payment1 = new Payment("PAY-1", TransactionType.DEBIT, "ACC-001", null,
                new BigDecimal("100.00"), "key1", Instant.now());
        payment1.complete();

        Payment payment2 = new Payment("PAY-2", TransactionType.CREDIT, null, "ACC-001",
                new BigDecimal("50.00"), "key2", Instant.now());
        payment2.complete();

        when(paymentRepository.findByAccountId(accountId, pageable))
                .thenReturn(entityPage);
        when(paymentMapper.toDomain(entity1)).thenReturn(payment1);
        when(paymentMapper.toDomain(entity2)).thenReturn(payment2);

        // When
        Page<Payment> result = paymentService.getPaymentHistory(accountId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getPaymentId()).isEqualTo("PAY-1");
        assertThat(result.getContent().get(1).getPaymentId()).isEqualTo("PAY-2");

        verify(paymentRepository).findByAccountId(accountId, pageable);
        verify(accountService, never()).getAccount(any()); // Should NOT call when payments exist
        verify(paymentMapper, times(2)).toDomain(any(PaymentEntity.class));
    }

    @Test
    void shouldThrowAccountNotFoundWhenGetPaymentHistoryForNonExistentAccount() {
        // Given
        String accountId = "NON-EXISTENT";
        Pageable pageable = PageRequest.of(0, 20);

        Page<PaymentEntity> emptyPage = new PageImpl<>(List.of());
        when(paymentRepository.findByAccountId(accountId, pageable))
                .thenReturn(emptyPage);

        when(accountService.getAccount(accountId))
                .thenThrow(new com.alpian.ledger.payment.exception.AccountNotFoundException("Account not found: " + accountId));

        // When/Then
        assertThatThrownBy(() -> paymentService.getPaymentHistory(accountId, pageable))
                .isInstanceOf(com.alpian.ledger.payment.exception.AccountNotFoundException.class)
                .hasMessageContaining("Account not found: NON-EXISTENT");

        verify(paymentRepository).findByAccountId(accountId, pageable);
        verify(accountService).getAccount(accountId);
    }

    @Test
    void shouldDelegateToCorrectStrategyWhenCreditTypeRequested() {
        // Given
        CreatePaymentRequest creditRequest = new CreatePaymentRequest(
                TransactionType.CREDIT,
                null,
                "ACC-002",
                new BigDecimal("50.00")
        );

        Payment creditPayment = new Payment(
                "PAY-456",
                TransactionType.CREDIT,
                null,
                "ACC-002",
                new BigDecimal("50.00"),
                idempotencyKey
        );
        creditPayment.complete();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(strategyFactory.getStrategy(TransactionType.CREDIT))
                .thenReturn(paymentStrategy);
        when(paymentStrategy.execute(creditRequest, idempotencyKey))
                .thenReturn(creditPayment);

        // When
        Payment result = paymentService.createPayment(creditRequest, idempotencyKey);

        // Then
        assertThat(result.getType()).isEqualTo(TransactionType.CREDIT);
        verify(strategyFactory).getStrategy(TransactionType.CREDIT);
    }

    @Test
    void shouldDelegateToCorrectStrategyWhenInternalTransferTypeRequested() {
        // Given
        CreatePaymentRequest transferRequest = new CreatePaymentRequest(
                TransactionType.INTERNAL_TRANSFER,
                "ACC-001",
                "ACC-002",
                new BigDecimal("75.00")
        );

        Payment transferPayment = new Payment(
                "PAY-789",
                TransactionType.INTERNAL_TRANSFER,
                "ACC-001",
                "ACC-002",
                new BigDecimal("75.00"),
                idempotencyKey
        );
        transferPayment.complete();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(strategyFactory.getStrategy(TransactionType.INTERNAL_TRANSFER))
                .thenReturn(paymentStrategy);
        when(paymentStrategy.execute(transferRequest, idempotencyKey))
                .thenReturn(transferPayment);

        // When
        Payment result = paymentService.createPayment(transferRequest, idempotencyKey);

        // Then
        assertThat(result.getType()).isEqualTo(TransactionType.INTERNAL_TRANSFER);
        verify(strategyFactory).getStrategy(TransactionType.INTERNAL_TRANSFER);
    }
}

