package com.aydog4nn.reserveflow.payment.scheduler;

import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentSchedulerTest {

    @Test
    void schedulingCallsServiceAutomaticallyMoreThanOnce() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            assertTrue(context.getBean(RecordingPaymentService.class).calls.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void schedulingCanBeDisabledForControlledTests() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("payment.scheduling.enabled", "false")));
            context.register(Config.class);
            context.refresh();
            assertTrue(context.getBeansOfType(PaymentScheduler.class).isEmpty());
        }
    }

    @Configuration
    @EnableScheduling
    @Import(PaymentScheduler.class)
    static class Config {

        @Bean
        RecordingPaymentService paymentService() {
            return new RecordingPaymentService();
        }
    }

    static class RecordingPaymentService implements IPaymentService {
        private final CountDownLatch calls = new CountDownLatch(2);

        @Override
        public PaymentResponse createPayment(CreatePaymentRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentResponse getPaymentById(java.util.UUID paymentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int completeDuePayments() {
            calls.countDown();
            return 0;
        }
    }
}
