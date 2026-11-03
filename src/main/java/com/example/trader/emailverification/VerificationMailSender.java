package com.example.trader.emailverification;

public interface VerificationMailSender {
    void send(String email, String code);
}
