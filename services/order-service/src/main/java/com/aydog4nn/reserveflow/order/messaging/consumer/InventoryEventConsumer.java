package com.aydog4nn.reserveflow.order.messaging.consumer;

import com.aydog4nn.reserveflow.order.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.order.messaging.dto.StockReservedEvent;
import com.aydog4nn.reserveflow.order.messaging.dto.RequestPaymentCommand;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.messaging.producer.PaymentCommandProducer;
import com.aydog4nn.reserveflow.order.service.abs.IOrderReservationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {

    private final IOrderReservationService reservationService;
    private final PaymentCommandProducer paymentCommandProducer;

    @RabbitListener(queues = RabbitMqConfiguration.STOCK_RESERVED_QUEUE)
    public void receive(StockReservedEvent event) {

        OrderResponse order = reservationService.handleStockReserved(
                event.orderId(),
                event.reservationId()
        );

        if (order.status() == OrderStatus.PAYMENT_PENDING) {
            paymentCommandProducer.sendRequestPayment(new RequestPaymentCommand(
                    order.orderId(), order.totalAmount(), order.currency(), order.paymentScenario()
            ));
        }
        System.out.println("Stok ayrıldı sonucu geldi: " + event);
    }



    public InventoryEventConsumer(IOrderReservationService reservationService, PaymentCommandProducer paymentCommandProducer) {
        this.reservationService = reservationService;
        this.paymentCommandProducer = paymentCommandProducer;
    }

}
