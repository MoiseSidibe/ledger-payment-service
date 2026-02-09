package com.alpian.ledger.payment.api.dto;

import java.util.List;

public record PaymentHistoryResponse(
        List<PaymentResponse> payments,
        int page,
        int size,
        int numberOfElements,
        long totalElements,
        int totalPages
) {}

