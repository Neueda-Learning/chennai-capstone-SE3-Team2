package com.yellow.trade.mappers;

/** A customer's registered bank account. The number never reaches a log. */
public class BankAccountRow {

    private String accountNumber;
    private String ifsc;
    private String holderName;

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    /** Deliberately not the fields: an accidental log line must not print the account number. */
    @Override
    public String toString() {
        return "BankAccountRow[redacted]";
    }
}
