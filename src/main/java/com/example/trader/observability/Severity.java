package com.example.trader.observability;

/**
 * LOW      → DEBUG
 * MEDIUM   → INFO
 * HIGH     → WARN
 * CRITICAL → ERROR
 * */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}