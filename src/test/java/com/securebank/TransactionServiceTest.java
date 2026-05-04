package com.securebank;

import com.securebank.dto.DepositRequest;
import com.securebank.dto.TransactionResponse;
import com.securebank.dto.TransferRequest;
import com.securebank.dto.WithdrawRequest;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.*;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private User alice;
    private Account aliceAccount;

    @BeforeEach
    void setUp() {
        alice = new User("alice", "alice@example.com", "hash", Role.CUSTOMER);
        aliceAccount = new Account("1111111111", AccountType.CHEQUE, alice);
        aliceAccount.setBalance(new BigDecimal("200.00"));

        // Set security context so ownership check passes
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deposit_increasesBalance() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.deposit(new DepositRequest(1L, new BigDecimal("50.00")), "alice");

        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("250.00");
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void withdraw_withSufficientFunds_decreasesBalance() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.withdraw(new WithdrawRequest(1L, new BigDecimal("100.00")), "alice");

        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("100.00");
        assertThat(response.type()).isEqualTo(TransactionType.WITHDRAWAL);
    }

    @Test
    void withdraw_withInsufficientFunds_throwsInsufficientFundsException() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));

        assertThatThrownBy(() -> transactionService.withdraw(new WithdrawRequest(1L, new BigDecimal("500.00")), "alice"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void transfer_updatesBoothAccountBalancesAtomically() {
        Account bobAccount = new Account("2222222222", AccountType.SAVINGS,
                new User("bob", "bob@example.com", "hash", Role.CUSTOMER));
        bobAccount.setBalance(new BigDecimal("50.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("75.00")), "alice");

        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("125.00");
        assertThat(bobAccount.getBalance()).isEqualByComparingTo("125.00");
        assertThat(response.type()).isEqualTo(TransactionType.TRANSFER);
        verify(accountRepository, times(2)).save(any(Account.class));
    }
}