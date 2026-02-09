package com.alpian.ledger.payment.api.dto;

import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String paymentId,
        TransactionType type,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        String direction  // "OUT", "IN", or null for non-history queries
) {
    public PaymentResponse(String paymentId, TransactionType type, String fromAccountId,
                           String toAccountId, BigDecimal amount, PaymentStatus status, Instant createdAt) {
        this(paymentId, type, fromAccountId, toAccountId, amount, status, createdAt, null);
    }
}
