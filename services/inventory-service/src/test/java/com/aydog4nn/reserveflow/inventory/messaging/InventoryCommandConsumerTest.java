package com.aydog4nn.reserveflow.inventory.messaging;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.consumer.InventoryCommandConsumer;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockReservedEvent;
import com.aydog4nn.reserveflow.inventory.messaging.producer.InventoryEventProducer;
import org.springframework.amqp.AmqpException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(OutputCaptureExtension.class)
class InventoryCommandConsumerTest {

    @Test
    void sendsRealReservationIdentifiersAfterServiceSuccess() {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), 3);
        IInventoryService inventoryService = mock(IInventoryService.class);
        InventoryEventProducer eventProducer = mock(InventoryEventProducer.class);
        Reservation reservation = mock(Reservation.class);
        UUID reservationId = UUID.randomUUID();
        when(reservation.getOrderId()).thenReturn(command.orderId());
        when(reservation.getId()).thenReturn(reservationId);
        when(inventoryService.reserveStock(command.orderId(), command.productId(), command.quantity()))
                .thenReturn(reservation);
        new InventoryCommandConsumer(inventoryService, eventProducer).receive(command);
        var order = inOrder(inventoryService, eventProducer);
        order.verify(inventoryService).reserveStock(command.orderId(), command.productId(), command.quantity());
        order.verify(eventProducer).sendStockReserved(new StockReservedEvent(command.orderId(), reservationId));
        verifyNoMoreInteractions(inventoryService, eventProducer);
    }

    @Test
    void propagatesFailureWithoutLoggingCompletion(CapturedOutput output) {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), 3);
        IInventoryService inventoryService = mock(IInventoryService.class);
        InventoryEventProducer eventProducer = mock(InventoryEventProducer.class);
        IllegalArgumentException failure = new IllegalArgumentException("Yetersiz stok");
        doThrow(failure).when(inventoryService)
                .reserveStock(command.orderId(), command.productId(), command.quantity());
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new InventoryCommandConsumer(inventoryService, eventProducer).receive(command)));
        verifyNoInteractions(eventProducer);
        assertFalse(output.getOut().contains("Stok rezervasyonu tamamlandı:"));
    }

    @Test
    void publishingFailureIsNotSwallowed() {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), 3);
        IInventoryService service = mock(IInventoryService.class);
        InventoryEventProducer producer = mock(InventoryEventProducer.class);
        Reservation reservation = mock(Reservation.class);
        UUID reservationId = UUID.randomUUID();
        when(service.reserveStock(command.orderId(), command.productId(), command.quantity())).thenReturn(reservation);
        when(reservation.getOrderId()).thenReturn(command.orderId());
        when(reservation.getId()).thenReturn(reservationId);
        AmqpException failure = new AmqpException("Test publication failure");
        doThrow(failure).when(producer).sendStockReserved(new StockReservedEvent(command.orderId(), reservationId));
        assertSame(failure, assertThrows(AmqpException.class,
                () -> new InventoryCommandConsumer(service, producer).receive(command)));
    }

    @Test
    void listensToLearningQueueWithInventoryDto() throws Exception {
        RabbitListener listener = InventoryCommandConsumer.class
                .getMethod("receive", ReserveStockCommand.class).getAnnotation(RabbitListener.class);
        assertArrayEquals(new String[]{RabbitMqConfiguration.RESERVE_STOCK_QUEUE}, listener.queues());
    }
}
