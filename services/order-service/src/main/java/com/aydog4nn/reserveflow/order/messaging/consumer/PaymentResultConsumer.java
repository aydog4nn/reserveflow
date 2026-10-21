package com.aydog4nn.reserveflow.order.messaging.consumer;

import com.aydog4nn.reserveflow.order.messaging.config.PaymentResultTopology;
import com.aydog4nn.reserveflow.order.messaging.dto.PaymentResultEvent;
import com.aydog4nn.reserveflow.order.service.impl.OrderPaymentService;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.messaging.dto.StockActionCommand;
import com.aydog4nn.reserveflow.order.messaging.producer.StockActionProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentResultConsumer.class);
    private final OrderPaymentService paymentService;
    private final StockActionProducer stockActions;

    public PaymentResultConsumer(OrderPaymentService paymentService, StockActionProducer stockActions) {
        this.paymentService = paymentService;
        this.stockActions = stockActions;
    }

    @RabbitListener(queues = PaymentResultTopology.QUEUE)
    public void receive(PaymentResultEvent event) {
        var order = paymentService.handlePaymentResult(event);
        StockActionCommand command = new StockActionCommand(order.orderId(), order.reservationId());
        if (order.status() == OrderStatus.STOCK_COMMIT_PENDING) {
            stockActions.commit(command);
        } else if (order.status() == OrderStatus.COMPENSATION_PENDING) {
            stockActions.release(command);
        }
        LOG.info("Ödeme sonucu siparişe işlendi: orderId={}, status={}", order.orderId(), order.status());
    }
}
