package com.alpian.ledger.payment.cucumber;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.api.dto.PaymentResponse;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared context for Cucumber scenarios
 * Stores state between step definitions
 */
@Component
@Data
public class CucumberContext {

    // Payment-related
    private ResponseEntity<PaymentResponse> lastPaymentResponse;
    private UUID lastIdempotencyKey;
    private String lastCreatedPaymentId;

    // Concurrent requests
    private int successfulPayments;
    private int failedPayments;
    private List<CreatePaymentRequest> preparedRequests = new ArrayList<>();
    private List<UUID> preparedIdempotencyKeys = new ArrayList<>();
    private List<ResponseEntity<PaymentResponse>> lastBatchResponses = new ArrayList<>();

    // Error handling
    private Exception lastException;
    private String lastErrorCode;
    private String lastErrorMessage;

    // Account balances
    private String lastCheckedAccountId;

    public void reset() {
        this.lastPaymentResponse = null;
        this.lastIdempotencyKey = null;
        this.lastCreatedPaymentId = null;
        this.successfulPayments = 0;
        this.failedPayments = 0;
        this.preparedRequests = new ArrayList<>();
        this.preparedIdempotencyKeys = new ArrayList<>();
        this.lastBatchResponses = new ArrayList<>();
        this.lastException = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.lastCheckedAccountId = null;
    }
}

