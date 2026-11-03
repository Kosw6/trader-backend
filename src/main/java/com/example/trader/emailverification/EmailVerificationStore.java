package com.example.trader.emailverification;

public interface EmailVerificationStore {
    boolean issue(String emailHash, String clientHash, String challengeId, String codeMac);
    boolean confirm(String emailHash, String challengeId, String codeMac, String proofHash);
    boolean consume(String emailHash, String proofHash);
    void cancel(String emailHash, String challengeId);
}
