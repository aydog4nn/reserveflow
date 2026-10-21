package com.aydog4nn.reserveflow.order.messaging.consumer;

import com.aydog4nn.reserveflow.order.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.order.messaging.dto.StockReservedEvent;
import com.aydog4nn.reserveflow.order.service.abs.IOrderReservationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {


    @RabbitListener(queues = RabbitMqConfiguration.STOCK_RESERVED_QUEUE)
    public void receive(StockReservedEvent event) {

        reservationService.handleStockReserved(
                event.orderId(),
                event.reservationId()
        );

        System.out.println("Stok ayrıldı sonucu geldi: " + event);
    }


    private final IOrderReservationService reservationService;

    public InventoryEventConsumer(IOrderReservationService reservationService) {
        this.reservationService = reservationService;
    }

}
