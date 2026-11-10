package com.jperez.lgsstorecrm.common.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException(String key) {
        super("Invalid Idempotency Key: " + key);
    }
}
