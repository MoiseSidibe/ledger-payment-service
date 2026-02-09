package com.alpian.ledger.payment.api;

import com.alpian.ledger.payment.api.dto.AccountResponse;
import com.alpian.ledger.payment.domain.Account;
import com.alpian.ledger.payment.service.AccountService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for account operations
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account API", description = "Endpoints for account operations")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details", description = "Retrieves account information including current balance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @Timed(value = "api.account.get", description = "Time taken to process get account API request")
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Account ID", required = true)
            @PathVariable String accountId) {

        log.info("Fetching account details for {}", accountId);

        Account account = accountService.getAccount(accountId);
        AccountResponse response = buildAccountResponse(account);

        return ResponseEntity.ok(response);
    }

    private AccountResponse buildAccountResponse(Account account) {
        return new AccountResponse(
                account.accountId(),
                account.balance()
        );
    }
}

