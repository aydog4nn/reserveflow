package com.aydog4nn.reserveflow.payment.controller;

import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final IPaymentService paymentService;

    public PaymentController(IPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createPayment(request);
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPaymentById(@PathVariable UUID paymentId) {
        return paymentService.getPaymentById(paymentId);
    }
}
