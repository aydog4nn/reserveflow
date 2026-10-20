package com.aydog4nn.reserveflow.inventory.messaging;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.consumer.InventoryCommandConsumer;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
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

@ExtendWith(OutputCaptureExtension.class)
class InventoryCommandConsumerTest {

    @Test
    void delegatesToServiceThenLogsCompletion(CapturedOutput output) {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), 3);
        IInventoryService inventoryService = mock(IInventoryService.class);
        new InventoryCommandConsumer(inventoryService).receive(command);
        verify(inventoryService).reserveStock(command.orderId(), command.productId(), command.quantity());
        verifyNoMoreInteractions(inventoryService);
        assertTrue(output.getOut().contains("Stok rezervasyonu tamamlandı: " + command));
    }

    @Test
    void propagatesFailureWithoutLoggingCompletion(CapturedOutput output) {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), 3);
        IInventoryService inventoryService = mock(IInventoryService.class);
        IllegalArgumentException failure = new IllegalArgumentException("Yetersiz stok");
        doThrow(failure).when(inventoryService)
                .reserveStock(command.orderId(), command.productId(), command.quantity());
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new InventoryCommandConsumer(inventoryService).receive(command)));
        assertFalse(output.getOut().contains("Stok rezervasyonu tamamlandı:"));
    }

    @Test
    void listensToLearningQueueWithInventoryDto() throws Exception {
        RabbitListener listener = InventoryCommandConsumer.class
                .getMethod("receive", ReserveStockCommand.class).getAnnotation(RabbitListener.class);
        assertArrayEquals(new String[]{RabbitMqConfiguration.RESERVE_STOCK_QUEUE}, listener.queues());
    }
}
