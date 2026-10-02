package com.yellow.exceptions;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.KycStatus;

public class AccountNotActiveException extends TradeException {

    private final AccountStatus actualStatus;
    private final KycStatus kycStatus;

    public AccountNotActiveException(AccountStatus actualStatus) {
        this(actualStatus, null, "Account not active");
    }

    /** Same ACC-403 as a suspended account, so the error catalogue is unchanged. */
    public static AccountNotActiveException kycNotVerified(AccountStatus actualStatus, KycStatus kycStatus) {
        return new AccountNotActiveException(actualStatus, kycStatus, "KYC not verified");
    }

    private AccountNotActiveException(AccountStatus actualStatus, KycStatus kycStatus, String message) {
        super("ACC-403", message);
        this.actualStatus = actualStatus;
        this.kycStatus = kycStatus;
    }

    public AccountStatus actualStatus() {
        return actualStatus;
    }

    /** Set only when KYC is the reason for the refusal. */
    public KycStatus kycStatus() {
        return kycStatus;
    }

}
