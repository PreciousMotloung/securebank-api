package com.securebank;

import com.securebank.dto.AccountResponse;
import com.securebank.dto.CreateAccountRequest;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.model.Account;
import com.securebank.model.AccountType;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.UserRepository;
import com.securebank.service.impl.AccountServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountServiceImpl accountService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAccount_success_returnsAccountResponse() {
        User alice = new User("alice", "alice@example.com", "hash", Role.CUSTOMER);
        CreateAccountRequest request = new CreateAccountRequest(AccountType.SAVINGS);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.createAccount(request, "alice");

        assertThat(response.type()).isEqualTo(AccountType.SAVINGS);
        assertThat(response.ownerUsername()).isEqualTo("alice");
        assertThat(response.accountNumber()).hasSize(10);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequest(AccountType.CHEQUE), "ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void getAccountById_byUnauthorizedUser_throwsAccessDeniedException() {
        User alice = new User("alice", "alice@example.com", "hash", Role.CUSTOMER);
        Account account = new Account("1234567890", AccountType.CHEQUE, alice);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        // SecurityContext is empty — hasAdminRole() returns false

        assertThatThrownBy(() -> accountService.getAccountById(1L, "bob"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAccountById_byOwner_returnsAccountResponse() {
        User alice = new User("alice", "alice@example.com", "hash", Role.CUSTOMER);
        Account account = new Account("1234567890", AccountType.CHEQUE, alice);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccountById(1L, "alice");

        assertThat(response.ownerUsername()).isEqualTo("alice");
    }
}