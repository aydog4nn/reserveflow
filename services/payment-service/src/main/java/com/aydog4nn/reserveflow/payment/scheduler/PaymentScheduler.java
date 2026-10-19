package com.aydog4nn.reserveflow.payment.scheduler;


import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentScheduler {

    private final IPaymentService paymentService;

    public PaymentScheduler(IPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 1000)
    public void completeDuePayments() {
        paymentService.completeDuePayments();
    }
}
