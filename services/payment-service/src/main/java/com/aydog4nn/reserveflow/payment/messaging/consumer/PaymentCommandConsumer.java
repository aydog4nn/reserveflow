package com.aydog4nn.reserveflow.payment.messaging.consumer;

import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import com.aydog4nn.reserveflow.payment.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.payment.messaging.dto.RequestPaymentCommand;
import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandConsumer {

    private final IPaymentService paymentService;
    private final Validator validator;

    public PaymentCommandConsumer(IPaymentService paymentService, Validator validator) {
        this.paymentService = paymentService;
        this.validator = validator;
    }


    @RabbitListener(queues = RabbitMqConfiguration.PAYMENT_QUEUE)
    public void receive(RequestPaymentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Ödeme talimatı zorunlu.");
        }
        var violations = validator.validate(command);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        CreatePaymentRequest request = new CreatePaymentRequest(
                command.orderId(),
                command.amount(),
                command.currency(),
                command.scenario()
        );

        PaymentResponse payment = paymentService.createPayment(request);

        System.out.println("Ödeme işlendi: " + payment);
    }
}
