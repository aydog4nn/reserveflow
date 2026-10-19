package com.aydog4nn.reserveflow.order.client;

import com.aydog4nn.reserveflow.order.client.dto.InventoryProductResponse;
import com.aydog4nn.reserveflow.order.client.exception.InventoryProductNotFoundException;
import com.aydog4nn.reserveflow.order.client.exception.InventoryUnavailableException;
import com.aydog4nn.reserveflow.order.configuration.InventoryClientConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpInventoryClientTest {

    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String BODY = """
            {"id":"22222222-2222-4222-8222-222222222222","sku":"DEMO-001","name":"Keyboard",
             "unitPrice":1500.00,"currency":"TRY","active":true,"onHand":10,"reserved":3,"available":7}
            """;

    private LocalValidatorFactoryBean validator;
    private MockRestServiceServer server;
    private HttpInventoryClient client;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpInventoryClient(builder.build(), validator);
    }

    @AfterEach
    void tearDown() {
        try {
            server.verify();
        } finally {
            validator.close();
        }
    }

    @Test
    void readsIndependentProductDtoAndIgnoresUnusedFields() {
        expect(withSuccess(BODY, MediaType.APPLICATION_JSON));
        InventoryProductResponse product = client.getProduct(PRODUCT_ID);
        assertEquals(PRODUCT_ID, product.id());
        assertEquals(new BigDecimal("1500.00"), product.unitPrice());
        assertEquals("TRY", product.currency());
        assertTrue(product.active());
    }

    @Test
    void leavesInactiveProductDecisionToService() {
        expect(withSuccess(BODY.replace("\"active\":true", "\"active\":false"), MediaType.APPLICATION_JSON));
        assertFalse(client.getProduct(PRODUCT_ID).active());
    }

    @Test
    void distinguishesMissingProductFromConnectionFailure() {
        expect(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(InventoryProductNotFoundException.class, () -> client.getProduct(PRODUCT_ID));
    }

    @Test
    void treatsServerErrorAsUnavailable() {
        expect(withServerError());
        assertThrows(InventoryUnavailableException.class, () -> client.getProduct(PRODUCT_ID));
    }

    @Test
    void doesNotAcceptRedirectAsProductResponse() {
        expect(withStatus(HttpStatus.FOUND));
        assertThrows(InventoryUnavailableException.class, () -> client.getProduct(PRODUCT_ID));
    }

    @Test
    void rejectsEmptyResponse() {
        expect(withNoContent());
        assertThrows(InventoryUnavailableException.class, () -> client.getProduct(PRODUCT_ID));
    }

    @Test
    void translatesTransportTimeout() {
        expect(request -> {
            throw new SocketTimeoutException("Simulated timeout");
        });
        assertThrows(InventoryUnavailableException.class, () -> client.getProduct(PRODUCT_ID));
    }

    @Test
    void rejectsNullIdWithoutMakingRequest() {
        assertThrows(IllegalArgumentException.class, () -> client.getProduct(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{",
            "{}",
            "{\"id\":\"22222222-2222-4222-8222-222222222222\",\"unitPrice\":-1,\"currency\":\"TRY\",\"active\":true}",
            "{\"id\":\"22222222-2222-4222-8222-222222222222\",\"unitPrice\":1.001,\"currency\":\"TRY\",\"active\":true}",
            "{\"id\":\"22222222-2222-4222-8222-222222222222\",\"unitPrice\":1,\"currency\":\"try\",\"active\":true}",
            "{\"id\":\"22222222-2222-4222-8222-222222222222\",\"unitPrice\":1,\"currency\":\"TRY\"}",
            "{\"id\":\"11111111-1111-4111-8111-111111111111\",\"unitPrice\":1,\"currency\":\"TRY\",\"active\":true}"
    })
    void rejectsMalformedOrInconsistentProduct(String body) {
        expect(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThrows(InventoryUnavailableException.class, () -> client.getProduct(PRODUCT_ID));
    }

    @Test
    void sendsRealHttpRequestUsingConfiguredClient() throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> request = new AtomicReference<>();
        httpServer.createContext("/api/v1/products/", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        httpServer.start();
        try {
            HttpInventoryClient realClient = realClient(httpServer, Duration.ofSeconds(2));
            assertEquals(PRODUCT_ID, realClient.getProduct(PRODUCT_ID).id());
            assertEquals("GET /api/v1/products/" + PRODUCT_ID, request.get());
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void configuredReadTimeoutStopsWaitingForSlowServer() throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        httpServer.createContext("/api/v1/products/", exchange -> {
            received.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
        try {
            HttpInventoryClient realClient = realClient(httpServer, Duration.ofMillis(200));
            assertThrows(InventoryUnavailableException.class, () -> realClient.getProduct(PRODUCT_ID));
            assertEquals(0L, received.getCount());
        } finally {
            release.countDown();
            httpServer.stop(0);
        }
    }

    @Test
    void rejectsInfiniteOrNegativeTimeoutConfiguration() {
        var configuration = new InventoryClientConfiguration();
        assertThrows(IllegalArgumentException.class, () -> configuration.inventoryRestClient(
                "http://inventory.test", Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> configuration.inventoryRestClient(
                "http://inventory.test", Duration.ofSeconds(1), Duration.ofSeconds(-1)));
    }

    private HttpInventoryClient realClient(HttpServer httpServer, Duration readTimeout) {
        RestClient restClient = new InventoryClientConfiguration().inventoryRestClient(
                "http://127.0.0.1:" + httpServer.getAddress().getPort(),
                Duration.ofSeconds(2), readTimeout);
        return new HttpInventoryClient(restClient, validator);
    }

    private void expect(ResponseCreator response) {
        server.expect(requestTo("http://inventory.test/api/v1/products/" + PRODUCT_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", "application/json"))
                .andRespond(response);
    }
}
