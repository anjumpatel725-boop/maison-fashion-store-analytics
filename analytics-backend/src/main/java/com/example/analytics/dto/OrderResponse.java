
package com.example.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        BigDecimal totalAmount,
        String paymentMethod,
        String paymentStatus,
        String orderStatus,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
    public record OrderItemResponse(
            Long productId,
            String productName,
            String sku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {}
}
