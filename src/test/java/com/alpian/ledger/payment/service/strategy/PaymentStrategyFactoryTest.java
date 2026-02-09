package com.alpian.ledger.payment.service.strategy;

import com.alpian.ledger.payment.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStrategyFactoryTest {

    @Mock
    private DebitPaymentStrategy debitStrategy;

    @Mock
    private CreditPaymentStrategy creditStrategy;

    @Mock
    private InternalTransferPaymentStrategy transferStrategy;

    private PaymentStrategyFactory factory;

    @BeforeEach
    void setUp() {
        when(debitStrategy.getType()).thenReturn(TransactionType.DEBIT);
        when(creditStrategy.getType()).thenReturn(TransactionType.CREDIT);
        when(transferStrategy.getType()).thenReturn(TransactionType.INTERNAL_TRANSFER);

        List<PaymentStrategy> strategies = Arrays.asList(debitStrategy, creditStrategy, transferStrategy);
        factory = new PaymentStrategyFactory(strategies);
    }

    @Test
    void shouldReturnDebitStrategyWhenDebitTypeRequested() {
        // When
        PaymentStrategy result = factory.getStrategy(TransactionType.DEBIT);

        // Then
        assertThat(result).isEqualTo(debitStrategy);
    }

    @Test
    void shouldReturnCreditStrategyWhenCreditTypeRequested() {
        // When
        PaymentStrategy result = factory.getStrategy(TransactionType.CREDIT);

        // Then
        assertThat(result).isEqualTo(creditStrategy);
    }

    @Test
    void shouldReturnTransferStrategyWhenInternalTransferTypeRequested() {
        // When
        PaymentStrategy result = factory.getStrategy(TransactionType.INTERNAL_TRANSFER);

        // Then
        assertThat(result).isEqualTo(transferStrategy);
    }

    @Test
    void shouldInitializeAllStrategiesWhenConstructed() {
        // When/Then
        assertThat(factory.getStrategy(TransactionType.DEBIT)).isNotNull();
        assertThat(factory.getStrategy(TransactionType.CREDIT)).isNotNull();
        assertThat(factory.getStrategy(TransactionType.INTERNAL_TRANSFER)).isNotNull();
    }
}

