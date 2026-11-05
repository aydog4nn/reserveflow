package com.aydog4nn.reserveflow.order.controller;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.exception.ApiExceptionHandler;
import com.aydog4nn.reserveflow.order.mapper.OrderMapper;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.service.impl.OrderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderQueryControllerTest {
    private final UUID id = UUID.randomUUID();
    private OrderRepository orders;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        var query = new OrderQueryService(orders, Mappers.getMapper(OrderMapper.class));
        mvc = MockMvcBuilders.standaloneSetup(new OrderQueryController(query))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void readsLatestPersistedStateWithoutWritingAnOrder() throws Exception {
        var order = new Order(UUID.randomUUID(), 3, new BigDecimal("1500.00"),
                new BigDecimal("4500.00"), "TRY", PaymentScenario.DELAY);
        ReflectionTestUtils.setField(order, "id", id);
        when(orders.findById(id)).thenReturn(Optional.of(order));
        mvc.perform(get("/api/v1/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_STOCK"))
                .andExpect(jsonPath("$.quantity").value(3));
        order.setStatus(OrderStatus.CONFIRMED);
        mvc.perform(get("/api/v1/orders/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(orders, times(2)).findById(id);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void missingOrderIs404WithProblemCode() throws Exception {
        when(orders.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/orders/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void malformedIdDoesNotQueryDatabase() throws Exception {
        mvc.perform(get("/api/v1/orders/not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(orders);
    }
}
