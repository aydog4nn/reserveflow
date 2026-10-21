package com.aydog4nn.reserveflow.order.messaging;

import com.aydog4nn.reserveflow.order.messaging.consumer.InventoryEventConsumer;
import com.aydog4nn.reserveflow.order.messaging.dto.StockReservedEvent;
import com.aydog4nn.reserveflow.order.service.abs.IOrderReservationService;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.messaging.dto.RequestPaymentCommand;
import com.aydog4nn.reserveflow.order.messaging.producer.PaymentCommandProducer;
import org.springframework.amqp.AmqpException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(OutputCaptureExtension.class)
class InventoryEventConsumerTest {

    @Test
    void logsReceivedReservationResult(CapturedOutput output) {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        IOrderReservationService reservationService = mock(IOrderReservationService.class);
        PaymentCommandProducer producer = mock(PaymentCommandProducer.class);
        when(reservationService.handleStockReserved(event.orderId(), event.reservationId()))
                .thenReturn(response(event, OrderStatus.PAYMENT_PENDING));
        new InventoryEventConsumer(reservationService, producer).receive(event);
        var sequence = inOrder(reservationService, producer);
        sequence.verify(reservationService).handleStockReserved(event.orderId(), event.reservationId());
        sequence.verify(producer).sendRequestPayment(
                new RequestPaymentCommand(event.orderId(), new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS));
        verifyNoMoreInteractions(reservationService, producer);
        assertTrue(output.getOut().contains("Stok ayrıldı sonucu geldi: " + event));
    }

    @Test
    void propagatesServiceErrorWithoutLoggingSuccess(CapturedOutput output) {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        IOrderReservationService service = mock(IOrderReservationService.class);
        PaymentCommandProducer producer = mock(PaymentCommandProducer.class);
        IllegalArgumentException failure = new IllegalArgumentException("Sipariş bulunamadı");
        doThrow(failure).when(service).handleStockReserved(event.orderId(), event.reservationId());
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new InventoryEventConsumer(service, producer).receive(event)));
        verifyNoInteractions(producer);
        assertFalse(output.getOut().contains("Stok ayrıldı sonucu geldi:"));
    }

    @Test
    void ignoresPaymentPublicationForAdvancedOrder() {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        IOrderReservationService service = mock(IOrderReservationService.class);
        PaymentCommandProducer producer = mock(PaymentCommandProducer.class);
        when(service.handleStockReserved(event.orderId(), event.reservationId()))
                .thenReturn(response(event, OrderStatus.CONFIRMED));
        new InventoryEventConsumer(service, producer).receive(event);
        verifyNoInteractions(producer);
    }

    @Test
    void propagatesPublicationFailure() {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        IOrderReservationService service = mock(IOrderReservationService.class);
        PaymentCommandProducer producer = mock(PaymentCommandProducer.class);
        when(service.handleStockReserved(event.orderId(), event.reservationId()))
                .thenReturn(response(event, OrderStatus.PAYMENT_PENDING));
        AmqpException failure = new AmqpException("Test outage");
        doThrow(failure).when(producer).sendRequestPayment(any());
        assertSame(failure, assertThrows(AmqpException.class,
                () -> new InventoryEventConsumer(service, producer).receive(event)));
    }

    private OrderResponse response(StockReservedEvent event, OrderStatus status) {
        return new OrderResponse(event.orderId(), UUID.randomUUID(), 3,
                new BigDecimal("1500.00"), new BigDecimal("4500.00"), "TRY",
                PaymentScenario.SUCCESS, status, event.reservationId(), null, null,
                Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void listensToInventoryResultQueue() throws Exception {
        RabbitListener listener = InventoryEventConsumer.class
                .getMethod("receive", StockReservedEvent.class).getAnnotation(RabbitListener.class);
        assertArrayEquals(new String[]{"order.stock-reserved.learning"}, listener.queues());
    }
}
