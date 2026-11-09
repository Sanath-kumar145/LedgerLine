package com.ledgerline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.dto.LedgerEntryDto;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.TransferResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.exception.AccountStateException;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.exception.InvalidTransactionException;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.IdempotencyRecordRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Service
public class TransferServiceImpl implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public TransferServiceImpl(AccountRepository accountRepository,
                               LedgerTransactionRepository transactionRepository,
                               LedgerEntryRepository entryRepository,
                               IdempotencyRecordRepository idempotencyRecordRepository,
                               AuditService auditService,
                               ObjectMapper objectMapper,
                               PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public TransferResponse transfer(TransferRequest request, String idempotencyKey, UserPrincipal currentUser, String ipAddress) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }

        // Validate Idempotency-Key
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new InvalidTransactionException("Idempotency-Key header is required for transfers.");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 64) {
            throw new InvalidTransactionException("Idempotency-Key must not exceed 64 characters.");
        }

        // Validate request structure
        if (request == null) {
            throw new InvalidTransactionException("Transfer request payload must be provided.");
        }
        if (request.getSourceAccountId() == null) {
            throw new InvalidTransactionException("Source account ID must be provided.");
        }
        if (request.getDestinationAccountId() == null) {
            throw new InvalidTransactionException("Destination account ID must be provided.");
        }
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new InvalidTransactionException("Cannot transfer funds to the same account.");
        }

        validateAmount(request.getAmount());
        BigDecimal normalizedAmount = request.getAmount().setScale(2, RoundingMode.UNNECESSARY);

        // Compute Canonical Request Hash (SHA-256)
        String requestHash = computeRequestHash(request, normalizedAmount);

        // Fast-path: Check if an idempotency record already exists before starting transaction
        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository
                .findByUserIdAndOperationTypeAndIdempotencyKey(currentUser.getId(), "TRANSFER", trimmedKey);
        if (existingOpt.isPresent()) {
            return handleExistingIdempotencyRecord(existingOpt.get(), requestHash, trimmedKey);
        }

        // Execute transfer inside database transaction
        try {
            return transactionTemplate.execute(status -> executeTransferInternal(request, normalizedAmount, trimmedKey, requestHash, currentUser, ipAddress));
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint violation on uk_idemp_user_op_key: concurrent request won the race
            log.info("Concurrent duplicate key detected for key '{}', retrieving committed result", trimmedKey);
            return idempotencyRecordRepository
                    .findByUserIdAndOperationTypeAndIdempotencyKey(currentUser.getId(), "TRANSFER", trimmedKey)
                    .map(rec -> handleExistingIdempotencyRecord(rec, requestHash, trimmedKey))
                    .orElseThrow(() -> ex);
        }
    }

    private TransferResponse executeTransferInternal(TransferRequest request,
                                                     BigDecimal amount,
                                                     String idempotencyKey,
                                                     String requestHash,
                                                     UserPrincipal currentUser,
                                                     String ipAddress) {
        // In-transaction check: if another request committed while this thread prepared to execute
        Optional<IdempotencyRecord> inTxOpt = idempotencyRecordRepository
                .findByUserIdAndOperationTypeAndIdempotencyKey(currentUser.getId(), "TRANSFER", idempotencyKey);
        if (inTxOpt.isPresent()) {
            return handleExistingIdempotencyRecord(inTxOpt.get(), requestHash, idempotencyKey);
        }

        Long srcId = request.getSourceAccountId();
        Long dstId = request.getDestinationAccountId();

        // 1. Deterministic Account Locking Order: lock min(src, dst) then max(src, dst)
        Long firstLockId = Math.min(srcId, dstId);
        Long secondLockId = Math.max(srcId, dstId);

        Account firstAccount = accountRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + firstLockId));
        Account secondAccount = accountRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + secondLockId));

        Account sourceAccount = srcId.equals(firstLockId) ? firstAccount : secondAccount;
        Account destinationAccount = dstId.equals(firstLockId) ? firstAccount : secondAccount;

        // Post-lock idempotency check: if thread was queued on locks behind identical request
        Optional<IdempotencyRecord> postLockOpt = idempotencyRecordRepository
                .findByUserIdAndOperationTypeAndIdempotencyKey(currentUser.getId(), "TRANSFER", idempotencyKey);
        if (postLockOpt.isPresent()) {
            return handleExistingIdempotencyRecord(postLockOpt.get(), requestHash, idempotencyKey);
        }

        // 2. Validate Authorization
        validateTransferAuthorization(sourceAccount, currentUser);

        // 3. Validate Account Types (Reject system clearing accounts)
        if (sourceAccount.getAccountType() == AccountType.SYSTEM_CLEARING) {
            throw new InvalidTransactionException("Cannot transfer from a system clearing account.");
        }
        if (destinationAccount.getAccountType() == AccountType.SYSTEM_CLEARING) {
            throw new InvalidTransactionException("Cannot transfer to a system clearing account.");
        }

        // 4. Validate Account Statuses
        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStateException("Source account is not active (status: " + sourceAccount.getStatus() + ").");
        }
        if (destinationAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStateException("Destination account is not active (status: " + destinationAccount.getStatus() + ").");
        }

        // 5. Validate Currency
        if (!sourceAccount.getCurrency().equalsIgnoreCase(destinationAccount.getCurrency())) {
            throw new InvalidTransactionException("Currency mismatch: Source account is in "
                    + sourceAccount.getCurrency() + " but destination account is in " + destinationAccount.getCurrency() + ".");
        }
        if (request.getCurrency() != null && !request.getCurrency().trim().isEmpty()) {
            if (!sourceAccount.getCurrency().equalsIgnoreCase(request.getCurrency().trim())) {
                throw new InvalidTransactionException("Currency mismatch: Request specified "
                        + request.getCurrency() + " but source account is in " + sourceAccount.getCurrency() + ".");
            }
        }
        String currency = sourceAccount.getCurrency();

        // 6. In-transaction balance check under exclusive row lock (Fresh Read Principle)
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            log.warn("Transfer refused for account {}: requested={}, available={}",
                    sourceAccount.getAccountNumber(), amount, sourceAccount.getBalance());

            // Autonomous audit event: committed in an independent transaction so refusal record persists on rollback
            auditService.recordAutonomousEvent(
                    "TRANSFER_REFUSED_INSUFFICIENT_FUNDS",
                    currentUser.getId(),
                    sourceAccount.getId(),
                    "Transfer of " + amount + " " + currency + " to account " + destinationAccount.getAccountNumber()
                            + " refused: insufficient funds (available: " + sourceAccount.getBalance() + ")",
                    ipAddress
            );

            throw new InsufficientFundsException(amount, sourceAccount.getBalance());
        }

        // 7. Update Balances
        BigDecimal sourceNewBalance = sourceAccount.getBalance().subtract(amount);
        BigDecimal destinationNewBalance = destinationAccount.getBalance().add(amount);

        sourceAccount.setBalance(sourceNewBalance);
        destinationAccount.setBalance(destinationNewBalance);

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // 8. Create LedgerTransaction journal header
        String description = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? request.getDescription().trim()
                : "Transfer from " + sourceAccount.getAccountNumber() + " to " + destinationAccount.getAccountNumber();

        LedgerTransaction transaction = new LedgerTransaction(
                currentUser.getId(),
                TransactionType.TRANSFER,
                amount,
                currency,
                description
        );
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.markCompleted();

        // 9. Post atomic balanced double entries:
        // Debit: Source Account (Sender balance decrease)
        // Credit: Destination Account (Receiver balance increase)
        LedgerEntry debitEntry = new LedgerEntry(sourceAccount, EntryType.DEBIT, amount, sourceNewBalance);
        LedgerEntry creditEntry = new LedgerEntry(destinationAccount, EntryType.CREDIT, amount, destinationNewBalance);

        transaction.addEntry(debitEntry);
        transaction.addEntry(creditEntry);

        LedgerTransaction savedTx = transactionRepository.save(transaction);

        // 10. Construct response
        List<LedgerEntryDto> entryDtos = List.of(
                new LedgerEntryDto(debitEntry.getId(), savedTx.getTransactionReference(), EntryType.DEBIT, amount, sourceNewBalance, debitEntry.getPostedAt(), savedTx.getDescription()),
                new LedgerEntryDto(creditEntry.getId(), savedTx.getTransactionReference(), EntryType.CREDIT, amount, destinationNewBalance, creditEntry.getPostedAt(), savedTx.getDescription())
        );

        TransferResponse response = new TransferResponse(
                savedTx.getTransactionReference(),
                TransactionType.TRANSFER,
                sourceAccount.getId(),
                destinationAccount.getId(),
                amount,
                currency,
                sourceNewBalance,
                destinationNewBalance,
                TransactionStatus.POSTED,
                savedTx.getDescription(),
                idempotencyKey,
                false,
                savedTx.getCompletedAt(),
                entryDtos
        );

        // 11. Persist Idempotency Record in the same transaction
        try {
            String responsePayload = objectMapper.writeValueAsString(response);
            IdempotencyRecord record = new IdempotencyRecord(
                    idempotencyKey,
                    currentUser.getId(),
                    "TRANSFER",
                    "/api/v1/transfers",
                    requestHash,
                    savedTx.getTransactionReference(),
                    "COMPLETED",
                    201,
                    responsePayload
            );
            idempotencyRecordRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transfer response for idempotency key '{}'", idempotencyKey, e);
            throw new IllegalStateException("Failed to serialize transfer response for idempotency persistence", e);
        }

        // 12. Record Audit Event (Consistent: participates in outer transaction, rolls back on failure)
        auditService.recordConsistentEvent(
                "TRANSFER_COMPLETED",
                currentUser.getId(),
                sourceAccount.getId(),
                "Transferred " + amount + " " + currency + " from " + sourceAccount.getAccountNumber()
                        + " to " + destinationAccount.getAccountNumber() + "; txRef=" + savedTx.getTransactionReference(),
                ipAddress
        );

        log.info("Transfer successful: txRef={} srcAccountId={} dstAccountId={} amount={} {} srcNewBalance={} dstNewBalance={}",
                savedTx.getTransactionReference(), sourceAccount.getId(), destinationAccount.getId(), amount, currency, sourceNewBalance, destinationNewBalance);

        return response;
    }

    private TransferResponse handleExistingIdempotencyRecord(IdempotencyRecord record, String currentHash, String key) {
        if (!record.getRequestHash().equals(currentHash)) {
            log.warn("Idempotency key conflict: key '{}' was previously used with hash '{}', current request hash is '{}'",
                    key, record.getRequestHash(), currentHash);
            throw new IdempotencyConflictException(
                    "Idempotency key '" + key + "' has already been used with a different request payload.", key);
        }

        try {
            TransferResponse cachedResponse = objectMapper.readValue(record.getResponsePayload(), TransferResponse.class);
            cachedResponse.setReplayed(true);
            log.info("Returning replayed transfer response for txRef={}, key='{}'",
                    cachedResponse.getTransactionReference(), key);
            return cachedResponse;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached idempotency response for key '{}'", key, e);
            throw new InvalidTransactionException("Failed to reconstruct cached idempotent response for key: " + key);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidTransactionException("Transfer amount must be provided.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transfer amount must be strictly greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new InvalidTransactionException("Transfer amount cannot have more than two decimal places.");
        }
    }

    private void validateTransferAuthorization(Account sourceAccount, UserPrincipal currentUser) {
        boolean isAuditor = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AUDITOR"));
        if (isAuditor) {
            log.warn("Security Alert: Auditor user id={} attempted to initiate a financial transfer", currentUser.getId());
            throw new AccessDeniedException("Access denied: Auditors cannot initiate financial transfers.");
        }

        if (!sourceAccount.isOwnedBy(currentUser.getId())) {
            log.warn("Security Alert: User id={} attempted unauthorized transfer from account id={} owned by user id={}",
                    currentUser.getId(), sourceAccount.getId(), sourceAccount.getUserId());
            throw new AccessDeniedException("Access denied: You do not own the source account.");
        }
    }

    private String computeRequestHash(TransferRequest req, BigDecimal amount) {
        String currency = req.getCurrency() != null ? req.getCurrency().trim().toUpperCase() : "INR";
        String desc = req.getDescription() != null ? req.getDescription().trim() : "";
        String canonical = req.getSourceAccountId() + "|"
                + req.getDestinationAccountId() + "|"
                + amount.toPlainString() + "|"
                + currency + "|"
                + desc;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

