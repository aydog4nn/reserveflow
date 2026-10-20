package com.aydog4nn.reserveflow.inventory.messaging;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.consumer.InventoryCommandConsumer;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class InventoryCommandConsumerTest {

    @Test
    void logsReceivedCommand(CapturedOutput output) {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), 3);
        new InventoryCommandConsumer().receive(command);
        assertTrue(output.getOut().contains("Stok ayırma mesajı geldi: " + command));
    }

    @Test
    void listensToLearningQueueWithInventoryDto() throws Exception {
        RabbitListener listener = InventoryCommandConsumer.class
                .getMethod("receive", ReserveStockCommand.class).getAnnotation(RabbitListener.class);
        assertArrayEquals(new String[]{RabbitMqConfiguration.RESERVE_STOCK_QUEUE}, listener.queues());
    }
}
