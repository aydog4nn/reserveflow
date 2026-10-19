package com.aydog4nn.reserveflow.payment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "İstek alanlarını kontrol edin.");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(
                error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, problem(HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY", "Geçerli JSON, UUID, tutar ve ödeme senaryosu gönderin."),
                headers, status, request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleMissing(PaymentNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(PaymentConflictException exception) {
        return response(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(PaymentTechnicalException.class)
    public ResponseEntity<ProblemDetail> handleTechnical(PaymentTechnicalException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_TECHNICAL_FAILURE", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT", "Ödeme bilgilerini kontrol edin.");
    }

    @ExceptionHandler({TransientDataAccessException.class, DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class})
    public ResponseEntity<ProblemDetail> handleUnavailable(RuntimeException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Ödeme verilerine şu anda erişilemiyor. Daha sonra tekrar deneyin.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        LOG.error("Ödeme API isteği beklenmeyen bir hata ile tamamlandı.", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "İşlem sırasında beklenmeyen bir hata oluştu.");
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(problem(status, code, detail));
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
