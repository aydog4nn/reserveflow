package com.aydog4nn.reserveflow.order.exception;

import com.aydog4nn.reserveflow.order.client.exception.InventoryProductNotFoundException;
import com.aydog4nn.reserveflow.order.client.exception.InventoryUnavailableException;
import com.aydog4nn.reserveflow.order.idempotency.IdempotencyConflictException;
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
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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
                "INVALID_REQUEST_BODY", "Geçerli JSON, UUID, tam sayı miktarı ve ödeme senaryosu gönderin."),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, problem(HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_HEADER", "Idempotency-Key başlığı zorunlu."),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            return handleExceptionInternal(exception, problem(HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR", "İşlem sırasında beklenmeyen bir hata oluştu."),
                    headers, status, request);
        }
        return handleExceptionInternal(exception, problem(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED", "İstek alanlarını kontrol edin. Idempotency-Key boş olamaz ve 128 karakteri aşamaz."),
                headers, status, request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(IdempotencyConflictException exception) {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InventoryProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleMissingProduct(InventoryProductNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrder(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ORDER", exception.getMessage());
    }

    @ExceptionHandler({InventoryUnavailableException.class, TransientDataAccessException.class,
            DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ProblemDetail> handleUnavailable(RuntimeException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Sipariş şu anda oluşturulamıyor. Aynı anahtarla daha sonra tekrar deneyin.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        LOG.error("Sipariş API isteği beklenmeyen bir hata ile tamamlandı.", exception);
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
