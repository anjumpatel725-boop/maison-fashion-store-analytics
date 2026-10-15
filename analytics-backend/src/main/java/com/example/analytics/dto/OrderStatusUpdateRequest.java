package com.example.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrderStatusUpdateRequest(

        @NotBlank
        @Pattern(
                regexp = "PLACED|CONFIRMED|SHIPPED|OUT_FOR_DELIVERY|DELIVERED|CANCELLED"
        )
        String orderStatus

) {
}
