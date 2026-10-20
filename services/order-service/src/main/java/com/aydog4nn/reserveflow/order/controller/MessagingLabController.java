package com.aydog4nn.reserveflow.order.controller;

import com.aydog4nn.reserveflow.order.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.order.messaging.producer.InventoryCommandProducer;
import org.springframework.amqp.AmqpException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lab/messages")
@ConditionalOnProperty(name = "reserveflow.messaging.lab-enabled", havingValue = "true")
public class MessagingLabController {

    private final InventoryCommandProducer producer;

    public MessagingLabController(InventoryCommandProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/reserve-stock")
    public ResponseEntity<String> reserveStock(@RequestBody ReserveStockCommand command) {
        if (command.orderId() == null || command.productId() == null || command.quantity() <= 0) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Sipariş/ürün kimlikleri zorunlu, miktar pozitif olmalı.");
            throw new org.springframework.web.ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
        }
        producer.sendReserveStock(command);
        return ResponseEntity.accepted().body(
                "Gönderim çağrısı tamamlandı. Bu yanıt stok ayrıldığı veya mesajın işlendiği anlamına gelmez.");
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ProblemDetail> handleMessagingFailure(AmqpException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Mesaj gönderimi tamamlanamadı; lab bağlantısını kontrol edin.");
        problem.setProperty("code", "MESSAGING_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
