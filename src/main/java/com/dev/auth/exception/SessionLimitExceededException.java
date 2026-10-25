package com.dev.auth.exception;

public class SessionLimitExceededException extends RuntimeException{
    public SessionLimitExceededException(String message) {
        super(message);
    }
}
