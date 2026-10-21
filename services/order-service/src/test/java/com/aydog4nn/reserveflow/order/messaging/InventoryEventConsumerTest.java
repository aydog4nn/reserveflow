package com.aydog4nn.reserveflow.order.messaging;

import com.aydog4nn.reserveflow.order.messaging.consumer.InventoryEventConsumer;
import com.aydog4nn.reserveflow.order.messaging.dto.StockReservedEvent;
import com.aydog4nn.reserveflow.order.service.abs.IOrderReservationService;
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

@ExtendWith(OutputCaptureExtension.class)
class InventoryEventConsumerTest {

    @Test
    void logsReceivedReservationResult(CapturedOutput output) {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        IOrderReservationService reservationService = mock(IOrderReservationService.class);
        new InventoryEventConsumer(reservationService).receive(event);
        verify(reservationService).handleStockReserved(event.orderId(), event.reservationId());
        verifyNoMoreInteractions(reservationService);
        assertTrue(output.getOut().contains("Stok ayrıldı sonucu geldi: " + event));
    }

    @Test
    void propagatesServiceErrorWithoutLoggingSuccess(CapturedOutput output) {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        IOrderReservationService service = mock(IOrderReservationService.class);
        IllegalArgumentException failure = new IllegalArgumentException("Sipariş bulunamadı");
        doThrow(failure).when(service).handleStockReserved(event.orderId(), event.reservationId());
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new InventoryEventConsumer(service).receive(event)));
        assertFalse(output.getOut().contains("Stok ayrıldı sonucu geldi:"));
    }

    @Test
    void listensToInventoryResultQueue() throws Exception {
        RabbitListener listener = InventoryEventConsumer.class
                .getMethod("receive", StockReservedEvent.class).getAnnotation(RabbitListener.class);
        assertArrayEquals(new String[]{"order.stock-reserved.learning"}, listener.queues());
    }
}
