package com.securebank.service.impl;

import com.securebank.dto.AccountResponse;
import com.securebank.dto.CreateAccountRequest;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.model.Account;
import com.securebank.model.User;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.UserRepository;
import com.securebank.service.AccountService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountServiceImpl(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsForCurrentUser(String username) {
        User user = findUser(username);
        return accountRepository.findAllByOwner(user).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, String username) {
        User user = findUser(username);
        Account account = new Account(generateUniqueAccountNumber(), request.type(), user);
        return AccountResponse.from(accountRepository.save(account));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id, String username) {
        Account account = findAccount(id);
        if (!account.getOwner().getUsername().equals(username) && !hasAdminRole()) {
            throw new AccessDeniedException("Access denied to account: " + id);
        }
        return AccountResponse.from(account);
    }

    @Override
    @Transactional
    public void deleteAccount(Long id) {
        Account account = findAccount(id);
        accountRepository.delete(account);
    }

    private String generateUniqueAccountNumber() {
        String number;
        do {
            long raw = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
            number = String.valueOf(raw);
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private Account findAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private boolean hasAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}