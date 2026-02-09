package com.alpian.ledger.payment.domain;

import java.math.BigDecimal;

public record Account(String accountId, BigDecimal balance) {
}
