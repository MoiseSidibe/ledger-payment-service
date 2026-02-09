package com.alpian.ledger.payment.infrastructure.mapper;

import com.alpian.ledger.payment.domain.Account;
import com.alpian.ledger.payment.infrastructure.persistence.AccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface AccountMapper {
    Account toDomain(AccountEntity entity);
}
