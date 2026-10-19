package com.aydog4nn.reserveflow.order.client;

import com.aydog4nn.reserveflow.order.client.dto.InventoryProductResponse;
import com.aydog4nn.reserveflow.order.client.exception.InventoryProductNotFoundException;
import com.aydog4nn.reserveflow.order.client.exception.InventoryUnavailableException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class HttpInventoryClient implements InventoryClient {

    private final RestClient restClient;
    private final Validator validator;

    public HttpInventoryClient(
            @Qualifier("inventoryRestClient") RestClient restClient, Validator validator) {
        this.restClient = restClient;
        this.validator = validator;
    }

    @Override
    public InventoryProductResponse getProduct(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Ürün kimliği zorunlu.");
        }
        InventoryProductResponse product = fetchProduct(productId);
        validateResponse(productId, product);
        return product;
    }

    private InventoryProductResponse fetchProduct(UUID productId) {
        try {
            return restClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new InventoryProductNotFoundException(productId);
                    })
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new InventoryUnavailableException("Inventory isteği tamamlanamadı.");
                    })
                    .body(InventoryProductResponse.class);
        } catch (RestClientException exception) {
            throw new InventoryUnavailableException("Inventory'den geçerli yanıt alınamadı.", exception);
        }
    }

    private void validateResponse(UUID productId, InventoryProductResponse product) {
        if (product == null || !validator.validate(product).isEmpty()
                || !productId.equals(product.id())) {
            throw new InventoryUnavailableException("Inventory ürün yanıtı sözleşmeye uygun değil.");
        }
    }
}
