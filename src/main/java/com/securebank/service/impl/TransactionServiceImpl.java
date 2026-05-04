package com.securebank.service.impl;

import com.securebank.dto.DepositRequest;
import com.securebank.dto.TransactionResponse;
import com.securebank.dto.TransferRequest;
import com.securebank.dto.WithdrawRequest;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.model.Account;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionStatus;
import com.securebank.model.TransactionType;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(AccountRepository accountRepository,
                                  TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request, String username) {
        Account account = getAccountAndVerifyOwnership(request.accountId(), username);
        account.setBalance(account.getBalance().add(request.amount()));
        accountRepository.save(account);
        return TransactionResponse.from(transactionRepository.save(buildTransaction(null, account, request.amount(), TransactionType.DEPOSIT)));
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request, String username) {
        Account account = getAccountAndVerifyOwnership(request.accountId(), username);
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance is " + account.getBalance() + ", attempted " + request.amount());
        }
        account.setBalance(account.getBalance().subtract(request.amount()));
        accountRepository.save(account);
        return TransactionResponse.from(transactionRepository.save(buildTransaction(account, null, request.amount(), TransactionType.WITHDRAWAL)));
    }

    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request, String username) {
        Account from = getAccountAndVerifyOwnership(request.fromAccountId(), username);
        Account to = accountRepository.findById(request.toAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.toAccountId()));

        if (from.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance is " + from.getBalance() + ", attempted " + request.amount());
        }

        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));
        accountRepository.save(from);
        accountRepository.save(to);

        return TransactionResponse.from(transactionRepository.save(buildTransaction(from, to, request.amount(), TransactionType.TRANSFER)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(Long accountId, String username, Pageable pageable) {
        Account account = getAccountAndVerifyOwnership(accountId, username);
        return transactionRepository.findByAccount(account, pageable).map(TransactionResponse::from);
    }

    private Account getAccountAndVerifyOwnership(Long accountId, String username) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        if (!account.getOwner().getUsername().equals(username) && !hasAdminRole()) {
            throw new AccessDeniedException("Access denied to account: " + accountId);
        }
        return account;
    }

    private Transaction buildTransaction(Account from, Account to, java.math.BigDecimal amount, TransactionType type) {
        Transaction tx = new Transaction();
        tx.setFromAccount(from);
        tx.setToAccount(to);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setReference(UUID.randomUUID().toString());
        return tx;
    }

    private boolean hasAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}