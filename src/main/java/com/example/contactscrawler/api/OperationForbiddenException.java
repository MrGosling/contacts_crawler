package com.example.contactscrawler.api;

/**
 * Операция запрещена конфигурацией (→ 403).
 */
public class OperationForbiddenException extends RuntimeException {
    public OperationForbiddenException(String message) {
        super(message);
    }
}
