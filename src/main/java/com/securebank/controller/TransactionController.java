package com.securebank.controller;

import com.securebank.dto.DepositRequest;
import com.securebank.dto.TransactionResponse;
import com.securebank.dto.TransferRequest;
import com.securebank.dto.WithdrawRequest;
import com.securebank.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(transactionService.deposit(request, authentication.getName()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(transactionService.withdraw(request, authentication.getName()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(transactionService.transfer(request, authentication.getName()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<Page<TransactionResponse>> getHistory(
            @PathVariable Long accountId,
            Authentication authentication,
            Pageable pageable) {
        return ResponseEntity.ok(
                transactionService.getTransactionHistory(accountId, authentication.getName(), pageable));
    }
}