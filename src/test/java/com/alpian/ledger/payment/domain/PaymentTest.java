package com.alpian.ledger.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    void shouldCreatePaymentInCreatedStateWhenInstantiated() {
        Payment payment = new Payment("P1", TransactionType.DEBIT, "A1", null, BigDecimal.TEN, "key1");
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
    }

    @Test
    void shouldCompletePaymentWhenInCreatedState() {
        Payment payment = new Payment("P1", TransactionType.DEBIT, "A1", null, BigDecimal.TEN, "key1");
        payment.complete();
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
    }

    @Test
    void shouldFailPaymentWhenInCreatedState() {
        Payment payment = new Payment("P1", TransactionType.DEBIT, "A1", null, BigDecimal.TEN, "key1");
        payment.fail();
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenCompletingAlreadyCompletedPayment() {
        Payment payment = new Payment("P1", TransactionType.DEBIT, "A1", null, BigDecimal.TEN, "key1");
        payment.complete();
        assertThrows(IllegalStateException.class, payment::complete);
    }

    @Test
    void shouldThrowExceptionWhenCompletingFailedPayment() {
        Payment payment = new Payment("P1", TransactionType.DEBIT, "A1", null, BigDecimal.TEN, "key1");
        payment.fail();
        assertThrows(IllegalStateException.class, payment::complete);
    }

    @Test
    void shouldThrowExceptionWhenFailingCompletedPayment() {
        Payment payment = new Payment("P1", TransactionType.DEBIT, "A1", null, BigDecimal.TEN, "key1");
        payment.complete();
        assertThrows(IllegalStateException.class, payment::fail);
    }

    @Test
    void shouldThrowExceptionWhenFailingAlreadyFailedPayment() {
        Payment payment = new Payment("P1", TransactionType.DEBIT, "A1", null, BigDecimal.TEN, "key1");
        payment.fail();
        assertThrows(IllegalStateException.class, payment::fail);
    }
}
