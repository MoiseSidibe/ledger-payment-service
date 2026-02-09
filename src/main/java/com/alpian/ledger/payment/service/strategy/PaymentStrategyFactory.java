package com.alpian.ledger.payment.service.strategy;

import com.alpian.ledger.payment.domain.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PaymentStrategyFactory {

    private final Map<TransactionType, PaymentStrategy> strategies;
    public PaymentStrategyFactory(List<PaymentStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        PaymentStrategy::getType,
                        Function.identity()
                ));
        log.info("Initialized PaymentStrategyFactory with {} strategies: {}",
                 strategies.size(), strategies.keySet());
    }

    public PaymentStrategy getStrategy(TransactionType type) {
        PaymentStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy found for transaction type: " + type);
        }
        return strategy;
    }
}

