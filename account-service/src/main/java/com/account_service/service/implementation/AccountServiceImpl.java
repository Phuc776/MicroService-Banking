package com.account_service.service.implementation;

import com.account_service.exception.*;
import com.account_service.external.SequenceService;
import com.account_service.external.TransactionService;
import com.account_service.external.UserService;
import com.account_service.model.AccountStatus;
import com.account_service.model.AccountType;
import com.account_service.model.dto.AccountDto;
import com.account_service.model.dto.AccountStatusUpdate;
import com.account_service.model.dto.external.TransactionResponse;
import com.account_service.model.dto.external.UserDto;
import com.account_service.model.dto.response.AccountResponse;
import com.account_service.model.entity.Account;
import com.account_service.model.mapper.AccountMapper;
import com.account_service.repository.AccountRepository;
import com.account_service.service.AccountService;
import com.account_service.utils.JwtUtil;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.service.spi.ServiceException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    @Value("${account.prefix}")
    private String accPrefix;

    private final AccountRepository accountRepository;
    private final UserService userService;
    private final SequenceService sequenceService;
    private final TransactionService transactionService;

    private final AccountMapper accountMapper = new AccountMapper();

    @Autowired
    private final JwtUtil jwtUtil;

    @Value("${spring.application.ok}")
    private String success;

    @Override
    public AccountResponse createAccount(AccountDto accountDto, HttpServletRequest request) {

        // Extract userId from JWT
        Long userId = jwtUtil.extractClaimsFromJwt(request);
        accountDto.setUserId(userId);

        // Fetch user information from user-service
        ResponseEntity<UserDto> user;
        try {
            user = userService.readUserById(userId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFound("User not found on the server");
        } catch (FeignException e) {
            log.error("FeignClient error: " + e.getMessage(), e);
            throw new ServiceException("An error occurred while communicating with the user service");
        }

        if (Objects.isNull(user.getBody())) {
            throw new ResourceNotFound("User not found on the server");
        }

        // Ensure account does not already exist for this user and account type
//        accountRepository.findAccountByUserIdAndAccountType(userId, AccountType.valueOf(accountDto.getAccountType()))
//                .ifPresent(account -> {
//                    log.error("Account already exists for user: " + userId);
//                    throw new ResourceConflict("Account already exists on the server");
//                });

        // Convert DTO to entity
        Account account = accountMapper.convertToEntity(accountDto);
        account.setAccountNumber(accPrefix + String.format("%07d", sequenceService.generateAccountNumber().getAccountNumber()));
        account.setAccountStatus(AccountStatus.PENDING);
        account.setAvailableBalance(BigDecimal.ZERO); // Default balance is zero
        account.setAccountType(AccountType.valueOf(accountDto.getAccountType()));

        // Save account and retrieve the saved entity
        Account savedAccount = accountRepository.save(account);

        // Convert saved entity back to DTO
        AccountDto savedAccountDto = accountMapper.convertToDto(savedAccount);
        savedAccountDto.setAccountType(savedAccount.getAccountType().toString());
        savedAccountDto.setAccountStatus(savedAccount.getAccountStatus().toString());

        // Return response with the saved account DTO
        return AccountResponse.builder()
                .responseCode(success)
                .message("Account created successfully.")
                .accountDto(savedAccountDto) // Use saved account entity for proper fields
                .build();
    }


    @Override
    public AccountResponse updateStatus(String accountNumber, AccountStatusUpdate accountUpdate, HttpServletRequest request) {

        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        Long userId = accountRepository.findAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"))
                .getUserId();
        // Check if the user is the owner of the account
        if(!userId.equals(authenticatedUserId)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập vào tài khoản này.");
        }
        return accountRepository.findAccountByAccountNumber(accountNumber)
                .map(account -> {
                    if (account.getAccountStatus().equals(AccountStatus.ACTIVE)) {
                        throw new AccountStatusException("Tài khoản đã được kích hoạt.");
                    }
                    if (account.getAvailableBalance().compareTo(BigDecimal.valueOf(1000)) < 0) {
                        throw new InSufficientFunds("Yêu cầu tài khoản có số dư tối thiểu là 1000");
                    }

                    // Update account status
                    account.setAccountStatus(accountUpdate.getAccountStatus());
                    Account updatedAccount = accountRepository.save(account);

                    // Convert updated account to DTO
                    AccountDto updatedAccountDto = accountMapper.convertToDto(updatedAccount);
                    updatedAccountDto.setAccountType(updatedAccount.getAccountType().toString());
                    updatedAccountDto.setAccountStatus(updatedAccount.getAccountStatus().toString());

                    return AccountResponse.builder()
                            .message("Cập nhật trạng thái tài khoản thành công.")
                            .responseCode(success)
                            .accountDto(updatedAccountDto) // Return updated account details
                            .build();
                }).orElseThrow(() -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"));
    }


    @Override
    public AccountDto readAccountByAccountNumber(String accountNumber, HttpServletRequest request) {

        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        Long userId = accountRepository.findAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"))
                .getUserId();
        // Check if the user is the owner of the account
        if(!userId.equals(authenticatedUserId)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập vào tài khoản này.");
        }
        return accountRepository.findAccountByAccountNumber(accountNumber)
                .map(account -> {
                    AccountDto accountDto = accountMapper.convertToDto(account);
                    accountDto.setAccountType(account.getAccountType().toString());
                    accountDto.setAccountStatus(account.getAccountStatus().toString());
                    return accountDto;
                })
                .orElseThrow(ResourceNotFound::new);
    }

    @Override
    public AccountResponse updateAccount(String accountNumber, AccountDto accountDto, HttpServletRequest request) {

        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        Long userId = accountRepository.findAccountByAccountNumber(accountNumber).get().getUserId();
        // Check if the user is the owner of the account
        if(!userId.equals(authenticatedUserId)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập vào tài khoản này.");
        }
        return accountRepository.findAccountByAccountNumber(accountNumber)
                .map(account -> {
                    BeanUtils.copyProperties(accountDto, account);
                    Account updatedAccount = accountRepository.save(account);

                    // Convert updated account entity to DTO
                    AccountDto updatedAccountDto = accountMapper.convertToDto(updatedAccount);
                    updatedAccountDto.setAccountType(updatedAccount.getAccountType().toString());
                    updatedAccountDto.setAccountStatus(updatedAccount.getAccountStatus().toString());

                    return AccountResponse.builder()
                            .responseCode(success)
                            .message("Cập nhật tài khoản thành công.")
                            .accountDto(updatedAccountDto) // Return updated account details
                            .build();
                }).orElseThrow(() -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"));
    }


    @Override
    public String getBalance(String accountNumber, HttpServletRequest request) {

        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        Long userId = accountRepository.findAccountByAccountNumber(accountNumber).orElseThrow(
                        () -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"))
                .getUserId();
        // Check if the user is the owner of the account
        if(!userId.equals(authenticatedUserId)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập vào tài khoản này.");
        }
        return accountRepository.findAccountByAccountNumber(accountNumber)
                .map(account -> account.getAvailableBalance().toString())
                .orElseThrow(ResourceNotFound::new);
    }

    @Override
    public List<TransactionResponse> getTransactionsFromAccountId(String accountId, HttpServletRequest request) {
        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        Long userId = accountRepository.findAccountByAccountNumber(accountId).orElseThrow(
                () -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"))
                .getUserId();

        // Check if the user is the owner of the account
        if(!userId.equals(authenticatedUserId)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập vào tài khoản này.");
        }
        return transactionService.getTransactionsFromAccountId(accountId);
    }

    @Override
    public AccountResponse closeAccount(String accountNumber, HttpServletRequest request) {

        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        Long userId = accountRepository.findAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"))
                .getUserId();
        // Check if the user is the owner of the account
        if(!userId.equals(authenticatedUserId)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập vào tài khoản này.");
        }
        return accountRepository.findAccountByAccountNumber(accountNumber)
                .map(account -> {
                    if (BigDecimal.valueOf(Double.parseDouble(getBalance(accountNumber,request))).compareTo(BigDecimal.ZERO) != 0) {
                        throw new AccountClosingException("Số dư tài khoản phải bằng 0 để đóng tài khoản");
                    }

                    account.setAccountStatus(AccountStatus.CLOSED);
                    Account closedAccount = accountRepository.save(account);

                    // Convert closed account to DTO
                    AccountDto closedAccountDto = accountMapper.convertToDto(closedAccount);
                    closedAccountDto.setAccountType(closedAccount.getAccountType().toString());
                    closedAccountDto.setAccountStatus(closedAccount.getAccountStatus().toString());

                    return AccountResponse.builder()
                            .message("Đóng tài khoản thành công")
                            .responseCode(success)
                            .accountDto(closedAccountDto) // Return closed account details
                            .build();
                }).orElseThrow(() -> new ResourceNotFound("Tài khoản không tồn tại trên hệ thống !"));
    }


    @Override
    public List<AccountDto> readAccountsByUserId(Long userId, HttpServletRequest request) {
        Long authenticatedUserId = jwtUtil.extractClaimsFromJwt(request);
        // Check if the user is the owner of the account
        if(!userId.equals(authenticatedUserId)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập.");
        }

        List<Account> accounts = accountRepository.findAccountsByUserId(userId);

        if (accounts.isEmpty()) {
            throw new ResourceNotFound("No accounts found for userId: " + userId);
        }

        return accounts.stream()
                .map(account -> {
                    AccountDto accountDto = accountMapper.convertToDto(account);
                    accountDto.setAccountStatus(account.getAccountStatus().toString());
                    accountDto.setAccountType(account.getAccountType().toString());
                    return accountDto;
                })
                .collect(Collectors.toList());
    }
}