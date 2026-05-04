package com.securebank.dto;

import com.securebank.model.Transaction;
import com.securebank.model.TransactionStatus;
import com.securebank.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String reference,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String fromAccountNumber,
        String toAccountNumber,
        LocalDateTime timestamp
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getReference(),
                t.getType(),
                t.getStatus(),
                t.getAmount(),
                t.getFromAccount() != null ? t.getFromAccount().getAccountNumber() : null,
                t.getToAccount() != null ? t.getToAccount().getAccountNumber() : null,
                t.getTimestamp()
        );
    }
}