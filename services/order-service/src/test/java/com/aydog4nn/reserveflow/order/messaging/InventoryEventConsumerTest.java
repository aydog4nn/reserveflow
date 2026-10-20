package com.aydog4nn.reserveflow.order.messaging;

import com.aydog4nn.reserveflow.order.messaging.consumer.InventoryEventConsumer;
import com.aydog4nn.reserveflow.order.messaging.dto.StockReservedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class InventoryEventConsumerTest {

    @Test
    void logsReceivedReservationResult(CapturedOutput output) {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        new InventoryEventConsumer().receive(event);
        assertTrue(output.getOut().contains("Stok ayrıldı sonucu geldi: " + event));
    }

    @Test
    void listensToInventoryResultQueue() throws Exception {
        RabbitListener listener = InventoryEventConsumer.class
                .getMethod("receive", StockReservedEvent.class).getAnnotation(RabbitListener.class);
        assertArrayEquals(new String[]{"order.stock-reserved.learning"}, listener.queues());
    }
}
