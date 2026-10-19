package com.aydog4nn.reserveflow.payment.service.abs;

import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import java.util.UUID;

public interface IPaymentService {

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse getPaymentById(UUID paymentId);

    int completeDuePayments();

}
