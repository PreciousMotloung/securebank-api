package com.securebank.service;

import com.securebank.dto.DepositRequest;
import com.securebank.dto.TransactionResponse;
import com.securebank.dto.TransferRequest;
import com.securebank.dto.WithdrawRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionService {
    TransactionResponse deposit(DepositRequest request, String username);
    TransactionResponse withdraw(WithdrawRequest request, String username);
    TransactionResponse transfer(TransferRequest request, String username);
    Page<TransactionResponse> getTransactionHistory(Long accountId, String username, Pageable pageable);
}