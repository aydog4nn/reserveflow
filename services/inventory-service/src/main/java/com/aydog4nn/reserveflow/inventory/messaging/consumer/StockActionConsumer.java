package com.aydog4nn.reserveflow.inventory.messaging.consumer;

import com.aydog4nn.reserveflow.inventory.messaging.config.StockActionTopology;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockActionCommand;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockFinalizedEvent;
import com.aydog4nn.reserveflow.inventory.messaging.producer.StockFinalizedProducer;
import com.aydog4nn.reserveflow.inventory.service.impl.InventoryStockActionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockActionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(StockActionConsumer.class);
    private final InventoryStockActionService service;
    private final StockFinalizedProducer results;

    public StockActionConsumer(InventoryStockActionService service, StockFinalizedProducer results) {
        this.service = service;
        this.results = results;
    }

    @RabbitListener(queues = StockActionTopology.COMMIT_QUEUE)
    public void commit(StockActionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Stok talimatı zorunlu.");
        }
        var reservation = service.commit(command.orderId(), command.reservationId());
        results.publish(new StockFinalizedEvent(
                reservation.getOrderId(), reservation.getId(), StockFinalizedEvent.Outcome.COMMITTED));
        LOG.info("Stok kesinleştirildi: orderId={}, reservationId={}",
                reservation.getOrderId(), reservation.getId());
    }

    @RabbitListener(queues = StockActionTopology.RELEASE_QUEUE)
    public void release(StockActionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Stok talimatı zorunlu.");
        }
        var reservation = service.release(command.orderId(), command.reservationId());
        results.publish(new StockFinalizedEvent(
                reservation.getOrderId(), reservation.getId(), StockFinalizedEvent.Outcome.RELEASED));
        LOG.info("Stok serbest bırakıldı: orderId={}, reservationId={}",
                reservation.getOrderId(), reservation.getId());
    }
}
