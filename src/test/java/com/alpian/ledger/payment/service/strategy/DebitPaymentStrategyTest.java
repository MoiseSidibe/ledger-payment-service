package com.alpian.ledger.payment.service.strategy;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.domain.TransactionType;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.exception.InsufficientFundsException;
import com.alpian.ledger.payment.infrastructure.mapper.PaymentMapper;
import com.alpian.ledger.payment.infrastructure.persistence.AccountEntity;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebitPaymentStrategyTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private DebitPaymentStrategy strategy;

    private CreatePaymentRequest request;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        request = new CreatePaymentRequest(
                TransactionType.DEBIT,
                "ACC-001",
                null,
                new BigDecimal("100.00")
        );
        idempotencyKey = "test-idempotency-key";
    }

    @Test
    void shouldReturnDebitWhenGetTypeCalled() {
        assertThat(strategy.getType()).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void shouldCreateDebitPaymentWhenValidRequest() {
        // Given
        when(accountRepository.deductBalance(eq("ACC-001"), eq(new BigDecimal("100.00"))))
                .thenReturn(1);
        when(paymentMapper.toEntity(any(Payment.class)))
                .thenReturn(new PaymentEntity());

        // When
        Payment result = strategy.execute(request, idempotencyKey);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(result.getFromAccountId()).isEqualTo("ACC-001");
        assertThat(result.getToAccountId()).isNull();
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(accountRepository).deductBalance("ACC-001", new BigDecimal("100.00"));
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
        // Given
        when(accountRepository.deductBalance(eq("ACC-001"), eq(new BigDecimal("100.00"))))
                .thenReturn(0);
        when(accountRepository.findByAccountId("ACC-001"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> strategy.execute(request, idempotencyKey))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository).deductBalance("ACC-001", new BigDecimal("100.00"));
        verify(accountRepository).findByAccountId("ACC-001");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowInsufficientFundsExceptionWhenBalanceInsufficient() {
        // Given
        when(accountRepository.deductBalance(eq("ACC-001"), eq(new BigDecimal("100.00"))))
                .thenReturn(0);
        when(accountRepository.findByAccountId("ACC-001"))
                .thenReturn(Optional.of(mock(AccountEntity.class)));

        // When/Then
        assertThatThrownBy(() -> strategy.execute(request, idempotencyKey))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        verify(accountRepository).deductBalance("ACC-001", new BigDecimal("100.00"));
        verify(accountRepository).findByAccountId("ACC-001");
        verify(paymentRepository, never()).save(any());
    }
}

