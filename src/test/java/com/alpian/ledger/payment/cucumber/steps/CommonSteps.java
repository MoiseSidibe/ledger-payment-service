package com.alpian.ledger.payment.cucumber.steps;

import com.alpian.ledger.payment.cucumber.CucumberContext;
import com.alpian.ledger.payment.infrastructure.persistence.AccountEntity;
import com.alpian.ledger.payment.infrastructure.persistence.AccountRepository;
import com.alpian.ledger.payment.infrastructure.persistence.OutboxEventRepository;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.datatable.DataTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class CommonSteps {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CucumberContext context;

    @Before
    public void beforeScenario() {
        log.info("Resetting scenario context");
        context.reset();
    }

    @Given("the payment service is running")
    public void thePaymentServiceIsRunning() {
        log.info("Payment service is running");
    }

    @Given("the following accounts exist:")
    @Transactional
    public void theFollowingAccountsExist(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps();

        log.info("Cleaning up existing test data...");
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        accountRepository.deleteAll();
        accountRepository.flush();

        for (Map<String, String> row : rows) {
            String accountId = row.get("accountId");
            BigDecimal balance = new BigDecimal(row.get("balance"));

            AccountEntity account = new AccountEntity(accountId, balance, null, null);
            accountRepository.save(account);

            log.info("Created account {} with balance {}", accountId, balance);
        }

        accountRepository.flush();
    }
}

