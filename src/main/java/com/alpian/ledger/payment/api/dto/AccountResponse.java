package com.alpian.ledger.payment.api.dto;

import java.math.BigDecimal;

public record AccountResponse(
        String accountId,
        BigDecimal balance
) {}
