package com.yellow.trade.mappers;

import java.time.LocalDate;

/** What a KYC check reads. Lives only for the length of one check; never logged. */
public class ApplicantRow {

    private String pan;
    private String name;
    private LocalDate dob;
    /** Null when the customer has no bank account on file. */
    private String bankAccountNumber;
    private String ifsc;

    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }

    /** Deliberately not the fields: an accidental log line must not print personal data. */
    @Override
    public String toString() {
        return "ApplicantRow[redacted]";
    }
}
