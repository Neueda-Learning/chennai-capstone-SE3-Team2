package com.yellow.trade.payments;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.KycStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.BankAccountRow;
import com.yellow.trade.mappers.PaymentMapper;
import com.yellow.trade.mappers.TransferRow;
import com.yellow.trade.payments.PaymentGateway.Direction;
import com.yellow.trade.security.CallerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Takes deposit and withdrawal requests. It records them PENDING and returns;
 * the gateway decides each one a few seconds later (PaymentJob), as a real
 * gateway's confirmation would arrive.
 *
 * Money moves only to and from the account's registered bank account, and
 * only for an ACTIVE account whose KYC has passed.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final AccountMapper accounts;
    private final PaymentMapper payments;
    private final CallerAccount caller;

    public PaymentService(AccountMapper accounts, PaymentMapper payments, CallerAccount caller) {
        this.accounts = accounts;
        this.payments = payments;
        this.caller = caller;
    }

    @Transactional(readOnly = true)
    public BankAccountResponse bankAccount(long accountId) {
        requireOwnAccount(accountId);
        BankAccountRow bank = requireBankAccount(accountId);
        String number = bank.getAccountNumber();
        return new BankAccountResponse(number.substring(number.length() - 4), bank.getIfsc(), bank.getHolderName());
    }

    /** Every transfer on the account, newest first. */
    @Transactional(readOnly = true)
    public List<TransferResponse> transfers(long accountId) {
        requireOwnAccount(accountId);
        return payments.findForClient(accountId).stream().map(TransferResponse::of).toList();
    }

    @Transactional
    public TransferResponse deposit(long accountId, TransferRequest request) {
        return request(accountId, Direction.DEPOSIT, request);
    }

    /**
     * Holds the amount at once, so it cannot also be spent on an order while
     * the gateway decides. Not enough available cash refuses it and writes
     * nothing.
     */
    @Transactional
    public TransferResponse withdraw(long accountId, TransferRequest request) {
        return request(accountId, Direction.WITHDRAWAL, request);
    }

    private TransferResponse request(long accountId, Direction direction, TransferRequest request) {
        AccountRow account = requireOwnAccount(accountId);
        requireMayMoveMoney(account);
        requireBankAccount(accountId);

        Long transferId = payments.insertPending(accountId, request.amount(), direction.name(), request.idempotencyKey());
        if (transferId == null) {
            return replay(accountId, direction, request);
        }
        if (direction == Direction.WITHDRAWAL && payments.hold(accountId, request.amount()) == 0) {
            // Thrown, not returned, so the PENDING row is rolled back with it.
            throw PaymentRefusedException.notEnoughAvailableCash();
        }
        log.info("{} {} requested on account {}", direction, transferId, accountId);
        return TransferResponse.of(payments.findById(transferId));
    }

    /**
     * The key was used before. The same transfer sent again -- a retry after a
     * lost response -- gets the first one back, so money never moves twice.
     * A different transfer under the same key is refused.
     */
    private TransferResponse replay(long accountId, Direction direction, TransferRequest request) {
        TransferRow first = payments.findByKey(accountId, request.idempotencyKey());
        if (first.getDirection().equals(direction.name()) && first.getAmount().compareTo(request.amount()) == 0) {
            return TransferResponse.of(first);
        }
        throw PaymentRefusedException.keyReused();
    }

    private AccountRow requireOwnAccount(long accountId) {
        AccountRow account = accounts.findById(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        if (!caller.canReach(accountId)) {
            // The same code and message as every other account route.
            log.warn("ACC-403: token for account {} addressed account {}", caller.accountId(), accountId);
            throw new AccountNotActiveException(account.getStatus());
        }
        return account;
    }

    private static void requireMayMoveMoney(AccountRow account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getStatus());
        }
        if (account.getKycStatus() != KycStatus.VERIFIED) {
            throw AccountNotActiveException.kycNotVerified(account.getStatus(), account.getKycStatus());
        }
    }

    private BankAccountRow requireBankAccount(long accountId) {
        BankAccountRow bank = payments.findBankAccount(accountId);
        if (bank == null) {
            throw PaymentRefusedException.noBankAccount();
        }
        return bank;
    }
}
