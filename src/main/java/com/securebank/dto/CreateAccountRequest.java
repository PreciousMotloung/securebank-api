package com.securebank.dto;

import com.securebank.model.AccountType;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotNull(message = "Account type is required")
        AccountType type
) {}