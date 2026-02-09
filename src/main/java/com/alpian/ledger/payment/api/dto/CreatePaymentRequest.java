package com.alpian.ledger.payment.api.dto;

import com.alpian.ledger.payment.domain.TransactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull(message = "Transaction type is required")
        TransactionType type,

        String fromAccountId,

        String toAccountId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) {
    /**
     * Custom validation ensuring account IDs match the transaction type requirements
     */
    @AssertTrue(message = "Account IDs must match transaction type: " +
                          "DEBIT requires fromAccountId only, " +
                          "CREDIT requires toAccountId only, " +
                          "INTERNAL_TRANSFER requires both and they must be different")
    public boolean isValidAccountIds() {
        if (type == null) {
            return true;
        }
        return switch (type) {
            case DEBIT -> fromAccountId != null && !fromAccountId.isBlank() && toAccountId == null;
            case CREDIT -> toAccountId != null && !toAccountId.isBlank() && fromAccountId == null;
            case INTERNAL_TRANSFER -> fromAccountId != null && !fromAccountId.isBlank() &&
                                      toAccountId != null && !toAccountId.isBlank() &&
                                      !fromAccountId.equals(toAccountId);
        };
    }
}
