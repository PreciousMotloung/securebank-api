package com.securebank.service;

import com.securebank.dto.AccountResponse;
import com.securebank.dto.CreateAccountRequest;

import java.util.List;

public interface AccountService {
    List<AccountResponse> getAccountsForCurrentUser(String username);
    AccountResponse createAccount(CreateAccountRequest request, String username);
    AccountResponse getAccountById(Long id, String username);
    void deleteAccount(Long id);
}