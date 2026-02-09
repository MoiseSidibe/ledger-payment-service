package com.alpian.ledger.payment.service.dto;

import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event payload for payment completion notifications
 * Supports DEBIT, CREDIT, and INTERNAL_TRANSFER transaction types
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private String paymentId;
    private TransactionType type;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private PaymentStatus status;
    private Instant timestamp;
}

