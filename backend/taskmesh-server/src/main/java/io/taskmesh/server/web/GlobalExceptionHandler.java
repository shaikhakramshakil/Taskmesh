package io.taskmesh.server.web;

import io.taskmesh.server.web.Dto.ErrorBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiExceptions.BackpressureException.class)
    public ResponseEntity<ErrorBody> backpressure(ApiExceptions.BackpressureException e) {
        return ResponseEntity.status(429).body(new ErrorBody("BACKPRESSURE", e.getMessage()));
    }

    @ExceptionHandler(ApiExceptions.NotFoundException.class)
    public ResponseEntity<ErrorBody> notFound(ApiExceptions.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ApiExceptions.ConflictException.class)
    public ResponseEntity<ErrorBody> conflict(ApiExceptions.ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler({ApiExceptions.BadRequestException.class, IllegalArgumentException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorBody> badRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("BAD_REQUEST",
                        e.getMessage() == null ? "Invalid request" : e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .sorted()
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("BAD_REQUEST", detail));
    }
}
