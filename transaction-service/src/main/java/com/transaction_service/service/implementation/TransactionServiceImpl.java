package com.transaction_service.service.implementation;

import com.transaction_service.exception.AccountStatusException;
import com.transaction_service.exception.GlobalErrorCode;
import com.transaction_service.exception.InsufficientBalance;
import com.transaction_service.exception.ResourceNotFound;
import com.transaction_service.external.AccountService;
import com.transaction_service.service.TransactionService;
import com.transaction_service.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.transaction_service.model.TransactionStatus;
import com.transaction_service.model.TransactionType;
import com.transaction_service.model.dto.TransactionDto;
import com.transaction_service.model.entity.Transaction;
import com.transaction_service.model.external.Account;
import com.transaction_service.model.mapper.TransactionMapper;
import com.transaction_service.model.response.Response;
import com.transaction_service.model.response.TransactionRequest;
import com.transaction_service.repository.TransactionRepository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    private final TransactionMapper transactionMapper = new TransactionMapper();

    @Autowired
    private final JwtUtil jwtUtil;

    @Value("${spring.application.ok}")
    private String ok;

    /**
     * Adds a transaction based on the provided TransactionDto
     * (This service is used for DEPOSIT and WITHDRAWAL Transaction type only).
     *
     * @param  transactionDto  the TransactionDto object containing the transaction details
     * @return                 a Response object indicating the success of the transaction
     * @throws ResourceNotFound     if the requested account is not found on the server
     * @throws AccountStatusException     if the account is inactive or closed
     * @throws InsufficientBalance     if there is insufficient balance in the account
     */
    @Override
    public Response addTransaction(TransactionDto transactionDto, HttpServletRequest request) {
        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        ResponseEntity<Account> response = accountService.readByAccountNumber(transactionDto.getAccountId());

        if (Objects.isNull(response.getBody())){
            throw new ResourceNotFound("Requested account not found on the server", GlobalErrorCode.NOT_FOUND);
        }

        Long userId = response.getBody().getUserId();

        if (!authenticatedUserId.equals(userId)) {
            log.error("User cannot access this account");
            throw new ResourceNotFound("User cannot access this account", GlobalErrorCode.FORBIDDEN);
        }
        Account account = response.getBody();
        Transaction transaction = transactionMapper.convertToEntity(transactionDto);
        if(transactionDto.getTransactionType().equals(TransactionType.DEPOSIT.toString())) {
            account.setAvailableBalance(account.getAvailableBalance().add(transactionDto.getAmount()));
        } else if (transactionDto.getTransactionType().equals(TransactionType.WITHDRAWAL.toString())) {
            if(!account.getAccountStatus().equals("ACTIVE")){
                log.error("account is either inactive/closed, cannot process the transaction");
                throw new AccountStatusException("account is inactive or closed");
            }
            if(account.getAvailableBalance().compareTo(transactionDto.getAmount()) < 0){
                log.error("insufficient balance in the account");
                throw new InsufficientBalance("Insufficient balance in the account");
            }
            transaction.setAmount(transactionDto.getAmount().negate());
            account.setAvailableBalance(account.getAvailableBalance().subtract(transactionDto.getAmount()));
        }

        transaction.setTransactionType(TransactionType.valueOf(transactionDto.getTransactionType()));
        transaction.setComments(transactionDto.getDescription());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setReferenceId(UUID.randomUUID().toString());

        accountService.updateAccount(transactionDto.getAccountId(), account);

        String messageCustom = "";
        if (transaction.getTransactionType().equals(TransactionType.DEPOSIT)) {
            messageCustom = "Đã nạp tiền thành công.";
        } else if (transaction.getTransactionType().equals(TransactionType.WITHDRAWAL)) {
            messageCustom = "Đã rút tiền thành công.";
        }
        return Response.builder()
                .message(messageCustom)
                .transactions(Collections.singletonList(transactionRepository.save(transaction)))
                .responseCode(ok).build();
    }

    /**
     * Completes the internal transaction by updating the status of each transaction
     * and saving them to the transaction repository.
     *
     * @param transactionDtos the list of transaction DTOs to be processed
     * @return a response indicating the completion of the transaction
     */
    @Override
    public Response internalTransaction(List<TransactionDto> transactionDtos, String transactionReference) {

        // Convert the list of transaction DTOs to entities
        List<Transaction> transactions = transactionMapper.convertToEntityList(transactionDtos);


        // Update the status of each transaction to 'COMPLETED'
        transactions.forEach(transaction -> {
            transaction.setTransactionType(TransactionType.INTERNAL_TRANSFER);
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setReferenceId(transactionReference);

            // Map the description from TransactionDto to comments in Transaction
            transactionDtos.stream()
                    .filter(dto -> dto.getAccountId().equals(transaction.getAccountId())) // Adjust this condition as needed
                    .findFirst().ifPresent(matchingDto -> transaction.setComments(matchingDto.getDescription()));

        });

        // Save all the completed transactions to the transaction repository


        // Return the response indicating the completion of the transaction
        return Response.builder()
                .responseCode(ok)
                .transactions(transactionRepository.saveAll(transactions))
                .message("Transaction completed successfully").build();

    }

    /**
     * Retrieves a list of transaction requests for a given account ID.
     *
     * @param accountId the ID of the account
     * @return a list of transaction requests
     */
    @Override
    public List<TransactionRequest> getTransaction(String accountId, HttpServletRequest request) {

        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        if(Objects.isNull(accountService.readByAccountNumber(accountId).getBody())){
            throw new ResourceNotFound("Requested account not found on the server", GlobalErrorCode.NOT_FOUND);
        }
        Long userId = accountService.readByAccountNumber(accountId).getBody().getUserId();
        if (!authenticatedUserId.equals(userId)) {
            throw new ResourceNotFound("User cannot access this account transaction.", GlobalErrorCode.FORBIDDEN);
        }
        return transactionRepository.findTransactionByAccountId(accountId)
                .stream().map(transaction -> {
                    TransactionRequest transactionRequest = new TransactionRequest();
                    BeanUtils.copyProperties(transaction, transactionRequest);
                    transactionRequest.setTransactionStatus(transaction.getStatus().toString());
                    transactionRequest.setLocalDateTime(transaction.getTransactionDate());
                    transactionRequest.setTransactionType(transaction.getTransactionType().toString());
                    return transactionRequest;
                }).collect(Collectors.toList());
    }

    /**
     * Retrieves a list of TransactionRequests based on a transaction reference.
     *
     * @param transactionReference The reference ID of the transaction
     * @return List of TransactionRequests matching the transaction reference
     */
    @Override
    public List<TransactionRequest> getTransactionByTransactionReference(String transactionReference) {

        return transactionRepository.findTransactionByReferenceId(transactionReference)
                .stream().map(transaction -> {
                    TransactionRequest transactionRequest = new TransactionRequest();
                    BeanUtils.copyProperties(transaction, transactionRequest);
                    transactionRequest.setTransactionStatus(transaction.getStatus().toString());
                    transactionRequest.setLocalDateTime(transaction.getTransactionDate());
                    transactionRequest.setTransactionType(transaction.getTransactionType().toString());
                    return transactionRequest;
                }).collect(Collectors.toList());
    }
}
