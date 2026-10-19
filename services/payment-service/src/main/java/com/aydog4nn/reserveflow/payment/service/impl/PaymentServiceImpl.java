package com.aydog4nn.reserveflow.payment.service.impl;

import com.aydog4nn.reserveflow.payment.domain.Payment;
import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.domain.PaymentStatus;
import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import com.aydog4nn.reserveflow.payment.mapper.PaymentMapper;
import com.aydog4nn.reserveflow.payment.exception.PaymentTechnicalException;
import com.aydog4nn.reserveflow.payment.exception.PaymentConflictException;
import com.aydog4nn.reserveflow.payment.exception.PaymentNotFoundException;
import com.aydog4nn.reserveflow.payment.repository.PaymentRepository;
import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements IPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    public PaymentServiceImpl(PaymentRepository paymentRepository, PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }


    private Optional<Payment> validateExistingPayment(CreatePaymentRequest request) {
        Optional<Payment> existingPayment =
                paymentRepository.findByOrderId(request.orderId());

        if (existingPayment.isEmpty()) {
            return existingPayment;
        }

        Payment payment = existingPayment.get();

        boolean sameAmount = payment.getAmount().compareTo(request.amount()) == 0;
        boolean sameCurrency = payment.getCurrency().equals(request.currency());
        boolean sameScenario = payment.getScenario() == request.scenario();

        if (!sameAmount || !sameCurrency || !sameScenario) {
            throw new PaymentConflictException();
        }

        return existingPayment;
    }

    private Payment createAndSavePayment(CreatePaymentRequest request) {
        Payment payment = new Payment(
                request.orderId(),
                request.amount(),
                request.currency(),
                request.scenario()
        );

        applyPaymentScenario(payment);

        return paymentRepository.save(payment);
    }

    private void applyPaymentScenario(Payment payment) {
        switch (payment.getScenario()) {
            case SUCCESS -> payment.setStatus(PaymentStatus.SUCCEEDED);
            case DECLINE -> {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Ödeme simülatör tarafından reddedildi.");
            }
            case DELAY -> payment.setReadyAt(Instant.now().plusSeconds(10));
            case TECHNICAL_FAILURE -> throw new PaymentTechnicalException();
        }
    }



    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Optional<Payment> existingPayment = validateExistingPayment(request);

        if (existingPayment.isPresent()) {
            return paymentMapper.toResponse(existingPayment.get());
        }

        Payment payment = createAndSavePayment(request);
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public int completeDuePayments() {
        List<Payment> payments =
                paymentRepository
                        .findTop100ByStatusAndScenarioAndReadyAtLessThanEqualOrderByReadyAtAsc(
                                PaymentStatus.PENDING,
                                PaymentScenario.DELAY,
                                Instant.now()
                        );

        payments.forEach(payment ->
                payment.setStatus(PaymentStatus.SUCCEEDED)
        );

        return payments.size();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Ödeme kimliği zorunlu.");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return paymentMapper.toResponse(payment);
    }

}
