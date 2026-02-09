package com.alpian.ledger.payment.service;

import com.alpian.ledger.payment.domain.Account;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.infrastructure.mapper.AccountMapper;
import com.alpian.ledger.payment.infrastructure.persistence.AccountEntity;
import com.alpian.ledger.payment.infrastructure.persistence.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    private AccountEntity accountEntity;
    private Account account;

    @BeforeEach
    void setUp() {
        accountEntity = new AccountEntity(
                "ACC-001",
                new BigDecimal("1000.00"),
                null,
                null
        );

        account = new Account("ACC-001", new BigDecimal("1000.00"));
    }

    @Test
    void shouldReturnAccountWhenAccountExists() {
        // Given
        String accountId = "ACC-001";
        when(accountRepository.findByAccountId(accountId))
                .thenReturn(Optional.of(accountEntity));
        when(accountMapper.toDomain(accountEntity))
                .thenReturn(account);

        // When
        Account result = accountService.getAccount(accountId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo("ACC-001");
        assertThat(result.balance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        verify(accountRepository).findByAccountId(accountId);
        verify(accountMapper).toDomain(accountEntity);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
        // Given
        String accountId = "NON-EXISTENT";
        when(accountRepository.findByAccountId(accountId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> accountService.getAccount(accountId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Account not found: NON-EXISTENT");

        verify(accountRepository).findByAccountId(accountId);
        verify(accountMapper, never()).toDomain(any());
    }
}

