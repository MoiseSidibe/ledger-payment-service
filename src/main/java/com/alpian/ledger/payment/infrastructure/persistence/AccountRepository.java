package com.alpian.ledger.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    Optional<AccountEntity> findByAccountId(String accountId);

    /**
     * Atomically deduct amount from account balance if sufficient funds exist.
     * @param accountId the account to deduct from
     * @param amount the amount to deduct
     * @return number of rows updated (1 = success, 0 = failed due to insufficient funds or account not found)
     */
    @Modifying
    @Query("UPDATE AccountEntity a " +
           "SET a.balance = a.balance - :amount " +
           "WHERE a.accountId = :accountId " +
           "AND a.balance >= :amount")
    int deductBalance(@Param("accountId") String accountId,
                      @Param("amount") BigDecimal amount);

    /**
     * Atomically add amount to account balance (for CREDIT and INTERNAL_TRANSFER destination).
     * @param accountId the account to credit
     * @param amount the amount to add
     * @return number of rows updated (1 = success, 0 = account not found)
     */
    @Modifying
    @Query("UPDATE AccountEntity a " +
           "SET a.balance = a.balance + :amount " +
           "WHERE a.accountId = :accountId")
    int creditBalance(@Param("accountId") String accountId,
                      @Param("amount") BigDecimal amount);

    /**
     * Lock both accounts in alphabetical order to prevent deadlocks in INTERNAL_TRANSFER.
     * @param accountId1 first account ID (should be alphabetically first)
     * @param accountId2 second account ID (should be alphabetically second)
     * @return list of locked account IDs
     */
    @Query(value = "SELECT account_id FROM accounts " +
                   "WHERE account_id IN (:accountId1, :accountId2) " +
                   "ORDER BY account_id " +
                   "FOR UPDATE",
           nativeQuery = true)
    List<String> lockAccountsInOrder(@Param("accountId1") String accountId1,
                                     @Param("accountId2") String accountId2);
}
