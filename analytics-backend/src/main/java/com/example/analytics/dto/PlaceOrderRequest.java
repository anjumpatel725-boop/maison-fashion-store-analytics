
package com.example.analytics.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record PlaceOrderRequest(
        @NotEmpty
        List<@Valid OrderItemRequest> items,

        @NotNull
        @Pattern(regexp = "UPI_DEMO|CARD_DEMO")
        String paymentMethod
) {
    public record OrderItemRequest(
            @NotNull
            Long productId,

            @Min(1)
            int quantity
    ) {}
}
