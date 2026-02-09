package com.alpian.ledger.payment.service.strategy;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.domain.TransactionType;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.infrastructure.mapper.PaymentMapper;
import com.alpian.ledger.payment.infrastructure.persistence.AccountRepository;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentEntity;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditPaymentStrategyTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private CreditPaymentStrategy strategy;

    private CreatePaymentRequest request;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        request = new CreatePaymentRequest(
                TransactionType.CREDIT,
                null,
                "ACC-002",
                new BigDecimal("50.00")
        );
        idempotencyKey = "test-idempotency-key";
    }

    @Test
    void shouldReturnCreditWhenGetTypeCalled() {
        assertThat(strategy.getType()).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void shouldCreateCreditPaymentWhenValidRequest() {
        // Given
        when(accountRepository.creditBalance(eq("ACC-002"), eq(new BigDecimal("50.00"))))
                .thenReturn(1);
        when(paymentMapper.toEntity(any(Payment.class)))
                .thenReturn(new PaymentEntity());

        // When
        Payment result = strategy.execute(request, idempotencyKey);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(result.getFromAccountId()).isNull();
        assertThat(result.getToAccountId()).isEqualTo("ACC-002");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(accountRepository).creditBalance("ACC-002", new BigDecimal("50.00"));
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
        // Given
        when(accountRepository.creditBalance(eq("ACC-002"), eq(new BigDecimal("50.00"))))
                .thenReturn(0);

        // When/Then
        assertThatThrownBy(() -> strategy.execute(request, idempotencyKey))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ACC-002");

        verify(accountRepository).creditBalance("ACC-002", new BigDecimal("50.00"));
        verify(paymentRepository, never()).save(any());
    }
}

