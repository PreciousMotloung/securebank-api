package com.securebank.dto;

import com.securebank.model.Account;
import com.securebank.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String accountNumber,
        AccountType type,
        BigDecimal balance,
        String currency,
        String ownerUsername,
        LocalDateTime createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getType(),
                account.getBalance(),
                account.getCurrency(),
                account.getOwner().getUsername(),
                account.getCreatedAt()
        );
    }
}