Feature: Payment Transactions Component Tests
  As a user of the ledger payment service
  I want to create payment transactions
  So that I can manage account balances

  Background:
    Given the payment service is running
    And the following accounts exist:
      | accountId | balance |
      | ACC001    | 1000.00 |
      | ACC002    | 500.00  |
      | ACC003    | 2000.00 |

  Scenario Outline: Process payment transaction and verify balance and history - <description>
    When I create a "<transactionType>" payment with the following details:
      | fromAccountId | <fromAccountId> |
      | toAccountId   | <toAccountId>   |
      | amount        | <amount>        |
    Then the payment should be created successfully
    And an outbox event should be published with payload:
      | type          | <transactionType> |
      | fromAccountId | <fromAccountId>   |
      | toAccountId   | <toAccountId>     |
      | amount        | <amount>          |
      | status        | COMPLETED         |
    When I request account details for "<accountToCheck1>"
    Then the account balance should be "<expectedBalance1>"
    When I request account details for "<accountToCheck2>"
    Then the account balance should be "<expectedBalance2>"
    When I request payment history for account "<accountToCheck1>"
    Then the payment history should contain the transaction
    And the payment should have direction "<direction1>"
    When I request payment history for account "<accountToCheck2>"
    Then the payment history should contain the transaction
    And the payment should have direction "<direction2>"

    Examples:
      | description               | transactionType   | fromAccountId | toAccountId | amount | accountToCheck1 | expectedBalance1 | direction1 | accountToCheck2 | expectedBalance2 | direction2 |
      | debit from ACC001         | DEBIT             | ACC001        | null        | 100.00 | ACC001          | 900.00           | OUT        | ACC001          | 900.00           | OUT        |
      | credit to ACC002          | CREDIT            | null          | ACC002      | 200.00 | ACC002          | 700.00           | IN         | ACC002          | 700.00           | IN         |
      | transfer ACC001 to ACC002 | INTERNAL_TRANSFER | ACC001        | ACC002      | 150.00 | ACC001          | 850.00           | OUT        | ACC002          | 650.00           | IN         |
      | debit from ACC002         | DEBIT             | ACC002        | null        | 50.00  | ACC002          | 450.00           | OUT        | ACC002          | 450.00           | OUT        |
      | credit to ACC001          | CREDIT            | null          | ACC001      | 75.00  | ACC001          | 1075.00          | IN         | ACC001          | 1075.00          | IN         |
      | transfer ACC002 to ACC001 | INTERNAL_TRANSFER | ACC002        | ACC001      | 100.00 | ACC002          | 400.00           | OUT        | ACC001          | 1100.00          | IN         |


  Scenario Outline: Prevent double spending with concurrent requests - <description>
    When I submit 5 identical "<transactionType>" payment requests simultaneously with idempotency key "<idempotencyKey>":
      | fromAccountId | <fromAccountId> |
      | toAccountId   | <toAccountId>   |
      | amount        | <amount>        |
    Then exactly 1 payment should succeed
    And 4 payments should fail
    And an outbox event should be published with payload:
      | type          | <transactionType> |
      | fromAccountId | <fromAccountId>   |
      | toAccountId   | <toAccountId>     |
      | amount        | <amount>          |
      | status        | COMPLETED         |
    When I request account details for "<accountToCheck1>"
    Then the account balance should be "<expectedBalance1>"
    When I request account details for "<accountToCheck2>"
    Then the account balance should be "<expectedBalance2>"
    When I request payment history for account "<historyAccount>"
    Then the payment history should contain exactly 1 transaction with idempotency key "<idempotencyKey>"

    Examples:
      | description         | transactionType   | fromAccountId | toAccountId | amount | idempotencyKey                       | accountToCheck1 | expectedBalance1 | accountToCheck2 | expectedBalance2 | historyAccount |
      | concurrent debit    | DEBIT             | ACC001        | null        | 100.00 | 550e8400-e29b-41d4-a716-446655440001 | ACC001          | 900.00           | ACC001          | 900.00           | ACC001         |
      | concurrent credit   | CREDIT            | null          | ACC002      | 200.00 | 550e8400-e29b-41d4-a716-446655440002 | ACC002          | 700.00           | ACC002          | 700.00           | ACC002         |
      | concurrent transfer | INTERNAL_TRANSFER | ACC001        | ACC002      | 150.00 | 550e8400-e29b-41d4-a716-446655440003 | ACC001          | 850.00           | ACC002          | 650.00           | ACC001         |


  Scenario Outline: Attempt payment transaction and verify error handling - <description>
    When I create a "<transactionType>" payment with the following details:
      | fromAccountId | <fromAccountId> |
      | toAccountId   | <toAccountId>   |
      | amount        | <amount>        |
    Then the payment should fail with error "<errorCode>"
    And the error message should contain "<errorMessagePart>"
    And no outbox event should be created
    When I request account details for "<accountToVerify1>"
    Then the account balance should remain "<originalBalance1>"
    When I request account details for "<accountToVerify2>"
    Then the account balance should remain "<originalBalance2>"

    Examples:
      | description                  | transactionType   | fromAccountId | toAccountId | amount  | errorCode          | errorMessagePart   | accountToVerify1 | originalBalance1 | accountToVerify2 | originalBalance2 |
      | insufficient funds debit     | DEBIT             | ACC001        | null        | 2000.00 | INSUFFICIENT_FUNDS | Insufficient funds | ACC001           | 1000.00          | ACC001           | 1000.00          |
      | debit from missing account   | DEBIT             | ACC-999       | null        | 100.00  | ACCOUNT_NOT_FOUND  | Account not found  | ACC001           | 1000.00          | ACC002           | 500.00           |
      | credit to missing account    | CREDIT            | null          | ACC-888     | 50.00   | ACCOUNT_NOT_FOUND  | Account not found  | ACC001           | 1000.00          | ACC002           | 500.00           |
      | insufficient funds transfer  | INTERNAL_TRANSFER | ACC001        | ACC002      | 1500.00 | INSUFFICIENT_FUNDS | Insufficient funds | ACC001           | 1000.00          | ACC002           | 500.00           |
      | transfer from missing sender | INTERNAL_TRANSFER | ACC-999       | ACC002      | 100.00  | ACCOUNT_NOT_FOUND  | Account not found  | ACC001           | 1000.00          | ACC002           | 500.00           |
      | transfer to missing receiver | INTERNAL_TRANSFER | ACC001        | ACC-777     | 100.00  | ACCOUNT_NOT_FOUND  | Account not found  | ACC001           | 1000.00          | ACC002           | 500.00           |
      | same account transfer        | INTERNAL_TRANSFER | ACC002        | ACC002      | 50.00   | VALIDATION_ERROR   | must be different  | ACC002           | 500.00           | ACC002           | 500.00           |
      | negative amount debit        | DEBIT             | ACC001        | null        | -50.00  | VALIDATION_ERROR   | must be positive   | ACC001           | 1000.00          | ACC001           | 1000.00          |
      | zero amount credit           | CREDIT            | null          | ACC002      | 0.00    | VALIDATION_ERROR   | must be positive   | ACC002           | 500.00           | ACC002           | 500.00           |


  Scenario: Balance consistency check under 100 concurrent payment requests
    # Initial balances: ACC001=1000.00, ACC002=500.00, ACC003=2000.00
    # Step 1: 25 transfers ACC001->ACC002 @ 5.00 each = -125.00 for ACC001, +125.00 for ACC002
    Given I prepare 25 payment requests with the following details:
      | transactionType | INTERNAL_TRANSFER |
      | fromAccountId   | ACC001            |
      | toAccountId     | ACC002            |
      | amount          | 5.00              |
    # Step 2: 25 transfers ACC002->ACC001 @ 10.00 each = +250.00 for ACC001, -250.00 for ACC002
    And I prepare 25 payment requests with the following details:
      | transactionType | INTERNAL_TRANSFER |
      | fromAccountId   | ACC002            |
      | toAccountId     | ACC001            |
      | amount          | 10.00             |
    # Step 3: 25 transfers ACC001->ACC003 @ 1.00 each = -25.00 for ACC001, +25.00 for ACC003
    And I prepare 25 payment requests with the following details:
      | transactionType | INTERNAL_TRANSFER |
      | fromAccountId   | ACC001            |
      | toAccountId     | ACC003            |
      | amount          | 1.00              |
    # Step 4: 15 debits from ACC002 @ 1.00 each = -15.00 for ACC002
    And I prepare 15 payment requests with the following details:
      | transactionType | DEBIT  |
      | fromAccountId   | ACC002 |
      | toAccountId     | null   |
      | amount          | 1.00   |
    # Step 5: 10 credits to ACC001 @ 20.00 each = +200.00 for ACC001
    And I prepare 10 payment requests with the following details:
      | transactionType | CREDIT |
      | fromAccountId   | null   |
      | toAccountId     | ACC001 |
      | amount          | 20.00  |
    When I submit all payment requests simultaneously
    Then 100 payment requests should succeed with COMPLETED status
    And 100 outbox events should be published with SENT status
    # Final balances: ACC001: 1000 - 125 + 250 - 25 + 200 = 1300.00
    When I request account details for "ACC001"
    Then the account balance should be "1300.00"
    # Final balances: ACC002: 500 + 125 - 250 - 15 = 360.00
    When I request account details for "ACC002"
    Then the account balance should be "360.00"
    # Final balances: ACC003: 2000 + 25 = 2025.00
    When I request account details for "ACC003"
    Then the account balance should be "2025.00"
    # ACC001 involved in: 25 OUT (to ACC002) + 25 IN (from ACC002) + 25 OUT (to ACC003) + 10 IN (credits) = 85 transactions
    When I request payment history for account "ACC001"
    Then the payment history should contain 85 transactions
    # ACC002 involved in: 25 IN (from ACC001) + 25 OUT (to ACC001) + 15 OUT (debits) = 65 transactions
    When I request payment history for account "ACC002"
    Then the payment history should contain 65 transactions
    # ACC003 involved in: 25 IN (from ACC001) = 25 transactions
    When I request payment history for account "ACC003"
    Then the payment history should contain 25 transactions
