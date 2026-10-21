package com.aydog4nn.reserveflow.order.messaging.consumer;

import com.aydog4nn.reserveflow.order.messaging.config.StockFinalizedTopology;
import com.aydog4nn.reserveflow.order.messaging.dto.StockFinalizedEvent;
import com.aydog4nn.reserveflow.order.service.impl.OrderCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockFinalizedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(StockFinalizedConsumer.class);
    private final OrderCompletionService service;

    public StockFinalizedConsumer(OrderCompletionService service) {
        this.service = service;
    }

    @RabbitListener(queues = StockFinalizedTopology.QUEUE)
    public void receive(StockFinalizedEvent event) {
        var order = service.handleStockFinalized(event);
        LOG.info("Sipariş kapandı: orderId={}, status={}", order.orderId(), order.status());
    }
}
