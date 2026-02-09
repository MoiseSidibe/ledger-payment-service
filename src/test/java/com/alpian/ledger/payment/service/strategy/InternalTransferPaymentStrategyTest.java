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
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalTransferPaymentStrategyTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private InternalTransferPaymentStrategy strategy;

    private CreatePaymentRequest request;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        request = new CreatePaymentRequest(
                TransactionType.INTERNAL_TRANSFER,
                "ACC-001",
                "ACC-002",
                new BigDecimal("75.00")
        );
        idempotencyKey = "test-idempotency-key";
    }

    @Test
    void shouldReturnInternalTransferWhenGetTypeCalled() {
        assertThat(strategy.getType()).isEqualTo(TransactionType.INTERNAL_TRANSFER);
    }

    @Test
    void shouldCreateTransferPaymentWhenValidRequest() {
        // Given
        when(accountRepository.lockAccountsInOrder("ACC-001", "ACC-002"))
                .thenReturn(Arrays.asList("ACC-001", "ACC-002"));
        when(accountRepository.deductBalance(eq("ACC-001"), eq(new BigDecimal("75.00"))))
                .thenReturn(1);
        when(accountRepository.creditBalance(eq("ACC-002"), eq(new BigDecimal("75.00"))))
                .thenReturn(1);
        when(paymentMapper.toEntity(any(Payment.class)))
                .thenReturn(new PaymentEntity());

        // When
        Payment result = strategy.execute(request, idempotencyKey);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.INTERNAL_TRANSFER);
        assertThat(result.getFromAccountId()).isEqualTo("ACC-001");
        assertThat(result.getToAccountId()).isEqualTo("ACC-002");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(accountRepository).lockAccountsInOrder("ACC-001", "ACC-002");
        verify(accountRepository).deductBalance("ACC-001", new BigDecimal("75.00"));
        verify(accountRepository).creditBalance("ACC-002", new BigDecimal("75.00"));
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void shouldLockAccountsInAlphabeticalOrderWhenReversedOrder() {
        // Given
        CreatePaymentRequest reverseRequest = new CreatePaymentRequest(
                TransactionType.INTERNAL_TRANSFER,
                "ACC-002",
                "ACC-001",
                new BigDecimal("75.00")
        );

        when(accountRepository.lockAccountsInOrder("ACC-001", "ACC-002"))
                .thenReturn(Arrays.asList("ACC-001", "ACC-002"));
        when(accountRepository.deductBalance(any(), any())).thenReturn(1);
        when(accountRepository.creditBalance(any(), any())).thenReturn(1);
        when(paymentMapper.toEntity(any(Payment.class))).thenReturn(new PaymentEntity());

        // When
        strategy.execute(reverseRequest, idempotencyKey);

        // Then
        verify(accountRepository).lockAccountsInOrder("ACC-001", "ACC-002");
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenFromAccountDoesNotExist() {
        // Given
        when(accountRepository.lockAccountsInOrder("ACC-001", "ACC-002"))
                .thenReturn(Collections.singletonList("ACC-002"));
        when(accountRepository.findByAccountId("ACC-001"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> strategy.execute(request, idempotencyKey))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ACC-001");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenToAccountDoesNotExist() {
        // Given
        when(accountRepository.lockAccountsInOrder("ACC-001", "ACC-002"))
                .thenReturn(Collections.singletonList("ACC-001"));
        when(accountRepository.findByAccountId("ACC-001"))
                .thenReturn(Optional.of(mock(AccountEntity.class)));

        // When/Then
        assertThatThrownBy(() -> strategy.execute(request, idempotencyKey))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ACC-002");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowInsufficientFundsExceptionWhenBalanceInsufficient() {
        // Given
        when(accountRepository.lockAccountsInOrder("ACC-001", "ACC-002"))
                .thenReturn(Arrays.asList("ACC-001", "ACC-002"));
        when(accountRepository.deductBalance(eq("ACC-001"), eq(new BigDecimal("75.00"))))
                .thenReturn(0);

        // When/Then
        assertThatThrownBy(() -> strategy.execute(request, idempotencyKey))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        verify(accountRepository).lockAccountsInOrder("ACC-001", "ACC-002");
        verify(accountRepository).deductBalance("ACC-001", new BigDecimal("75.00"));
        verify(accountRepository, never()).creditBalance(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenCreditFailsAfterDeduction() {
        // Given
        when(accountRepository.lockAccountsInOrder("ACC-001", "ACC-002"))
                .thenReturn(Arrays.asList("ACC-001", "ACC-002"));
        when(accountRepository.deductBalance(eq("ACC-001"), eq(new BigDecimal("75.00"))))
                .thenReturn(1);
        when(accountRepository.creditBalance(eq("ACC-002"), eq(new BigDecimal("75.00"))))
                .thenReturn(0);

        // When/Then
        assertThatThrownBy(() -> strategy.execute(request, idempotencyKey))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ACC-002");

        verify(accountRepository).deductBalance("ACC-001", new BigDecimal("75.00"));
        verify(accountRepository).creditBalance("ACC-002", new BigDecimal("75.00"));
        verify(paymentRepository, never()).save(any());
    }
}

