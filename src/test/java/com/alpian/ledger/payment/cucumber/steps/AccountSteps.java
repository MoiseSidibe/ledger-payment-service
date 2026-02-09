package com.alpian.ledger.payment.cucumber.steps;

import com.alpian.ledger.payment.api.dto.AccountResponse;
import com.alpian.ledger.payment.cucumber.CucumberContext;
import com.alpian.ledger.payment.cucumber.CucumberSpringConfiguration;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RequiredArgsConstructor
public class AccountSteps {

    private final CucumberSpringConfiguration config;
    private final CucumberContext context;
    private final RestTemplate restTemplate = new RestTemplate();

    private AccountResponse lastAccountResponse;

    @When("I request account details for {string}")
    public void iRequestAccountDetailsFor(String accountId) {
        String url = config.getBaseUrl() + "/accounts/" + accountId;

        ResponseEntity<AccountResponse> response = restTemplate.getForEntity(
                url,
                AccountResponse.class
        );

        lastAccountResponse = response.getBody();
        context.setLastCheckedAccountId(accountId);

        log.info("Fetched account {} with balance {}", accountId,
                lastAccountResponse != null ? lastAccountResponse.balance() : null);
    }

    @Then("the account balance should be {string}")
    public void theAccountBalanceShouldBe(String expectedBalance) {
        assertThat(lastAccountResponse).isNotNull();
        assertThat(lastAccountResponse.balance())
                .isEqualByComparingTo(new BigDecimal(expectedBalance));

        log.info("Verified balance: expected={}, actual={}", expectedBalance, lastAccountResponse.balance());
    }

    @Then("the account balance should remain {string}")
    public void theAccountBalanceShouldRemain(String expectedBalance) {
        theAccountBalanceShouldBe(expectedBalance);
    }

    @Then("the account balance should still be {string}")
    public void theAccountBalanceShouldStillBe(String expectedBalance) {
        theAccountBalanceShouldBe(expectedBalance);
    }
}

