package com.aydog4nn.reserveflow.order.controller;

import com.aydog4nn.reserveflow.order.exception.ApiExceptionHandler;
import com.aydog4nn.reserveflow.order.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.order.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.order.messaging.producer.InventoryCommandProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessagingLabControllerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String BODY = """
            {"orderId":"%s","productId":"%s","quantity":3}
            """.formatted(ORDER_ID, PRODUCT_ID);

    private final InventoryCommandProducer producer = mock(InventoryCommandProducer.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MessagingLabController(producer))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void forwardsCommandWithoutCreatingOrder() throws Exception {
        mvc.perform(post("/api/v1/lab/messages/reserve-stock")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted());
        verify(producer).sendReserveStock(new ReserveStockCommand(ORDER_ID, PRODUCT_ID, 3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"quantity\":0}", "{\"quantity\":-1}", "{"})
    void rejectsInvalidInputWithoutPublishing(String body) throws Exception {
        mvc.perform(post("/api/v1/lab/messages/reserve-stock")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(producer);
    }

    @Test
    void reportsBrokerFailure() throws Exception {
        doThrow(new AmqpConnectException(new ConnectException("private broker details")))
                .when(producer).sendReserveStock(any());
        mvc.perform(post("/api/v1/lab/messages/reserve-stock")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MESSAGING_UNAVAILABLE"));
    }

    @Test
    void labEndpointIsOptIn() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(InventoryCommandProducer.class, () -> producer)
                .withUserConfiguration(MessagingLabController.class);
        runner.run(context -> assertTrue(context.getBeansOfType(MessagingLabController.class).isEmpty()));
        runner.withPropertyValues("reserveflow.messaging.lab-enabled=true")
                .run(context -> assertEquals(1, context.getBeansOfType(MessagingLabController.class).size()));
    }

    @Test
    void userProducerUsesConfiguredRoutingAndConverterProducesJson() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        ReserveStockCommand command = new ReserveStockCommand(ORDER_ID, PRODUCT_ID, 3);
        new InventoryCommandProducer(template).sendReserveStock(command);
        verify(template).convertAndSend("reserveflow.commands", "inventory.reserve.v1", command);
        RabbitMqConfiguration config = new RabbitMqConfiguration();
        var message = config.rabbitMessageConverter().toMessage(command, new MessageProperties());
        assertEquals("application/json", message.getMessageProperties().getContentType());
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains(ORDER_ID.toString()));
        assertTrue(json.contains(PRODUCT_ID.toString()));
        var binding = config.reserveStockBinding(config.reserveStockQueue(), config.commandsExchange());
        assertEquals("inventory.reserve.learning", binding.getDestination());
        assertEquals("reserveflow.commands", binding.getExchange());
        assertEquals("inventory.reserve.v1", binding.getRoutingKey());
    }
}
