package com.example.urlshortener.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validation failed");
        Map<String, String> fieldErrors = new HashMap<>();
        List<FieldError> errors = ex.getBindingResult().getFieldErrors();
        for (FieldError fe : errors) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("fields", fieldErrors);
        attachRequestId(body);
        return new ResponseEntity<>(body, new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
            InvalidUrlException.class,
            InvalidShortCodeException.class
    })
    public ResponseEntity<Object> handleBadRequest(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        attachRequestId(body);
        return new ResponseEntity<>(body, new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ShortCodeConflictException.class)
    public ResponseEntity<Object> handleConflict(ShortCodeConflictException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        attachRequestId(body);
        return new ResponseEntity<>(body, new HttpHeaders(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(UrlNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Not found");
        attachRequestId(body);
        return new ResponseEntity<>(body, new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex) {
        // Do not leak internal messages
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Internal server error");
        attachRequestId(body);
        return new ResponseEntity<>(body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void attachRequestId(Map<String, Object> body) {
        String rid = MDC.get("requestId");
        if (rid != null && !rid.isBlank()) {
            body.put("requestId", rid);
        }
    }
}