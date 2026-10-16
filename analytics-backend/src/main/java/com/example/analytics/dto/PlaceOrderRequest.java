
package com.example.analytics.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PlaceOrderRequest(

        @NotBlank
        String customerName,

        @NotBlank
        @Email
        String customerEmail,

        @NotBlank
        @Pattern(regexp = "[0-9]{10}",
                 message = "Phone must contain 10 digits")
        String phone,

        @NotBlank
        String shippingAddress,

        @NotBlank
        String city,

        @NotBlank
        @Pattern(regexp = "[0-9]{6}",
                 message = "Postal code must contain 6 digits")
        String postalCode,

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
