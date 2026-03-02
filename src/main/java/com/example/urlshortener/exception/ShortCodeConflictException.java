package com.example.urlshortener.exception;

public class ShortCodeConflictException extends RuntimeException {
    public ShortCodeConflictException(String message) {
        super(message);
    }

    public ShortCodeConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}