package com.alpian.ledger.payment.service;

import com.alpian.ledger.payment.domain.Account;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.infrastructure.mapper.AccountMapper;
import com.alpian.ledger.payment.infrastructure.persistence.AccountEntity;
import com.alpian.ledger.payment.infrastructure.persistence.AccountRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional(readOnly = true)
    @Timed(value = "account.fetch", description = "Time taken to fetch account details")
    public Account getAccount(String accountId) {
        log.info("Fetching account details for {}", accountId);
        AccountEntity accountEntity = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return accountMapper.toDomain(accountEntity);
    }
}

