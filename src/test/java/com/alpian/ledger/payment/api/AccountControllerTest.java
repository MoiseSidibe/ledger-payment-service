package com.alpian.ledger.payment.api;

import com.alpian.ledger.payment.domain.Account;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void shouldReturnAccountWhenAccountExists() throws Exception {
        // Given
        Account account = new Account("ACC-001", new BigDecimal("1000.00"));
        when(accountService.getAccount("ACC-001")).thenReturn(account);

        // When/Then
        mockMvc.perform(get("/accounts/ACC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-001"))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    void shouldReturnBadRequestWhenAccountNotFound() throws Exception {
        // Given
        when(accountService.getAccount("ACC-999"))
                .thenThrow(new AccountNotFoundException("ACC-999"));

        // When/Then
        mockMvc.perform(get("/accounts/ACC-999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }
}

