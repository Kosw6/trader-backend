package com.example.trader.policy;

public record PolicyAcceptance(String version, String termsSha256, String privacySha256,
                               Boolean termsAccepted, Boolean personalDataAccepted, Boolean age14Confirmed) implements java.io.Serializable {}
