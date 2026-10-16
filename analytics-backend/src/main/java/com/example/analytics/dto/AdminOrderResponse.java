
package com.example.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderResponse(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        String customerEmail,
        String phone,
        String shippingAddress,
        String city,
        String postalCode,
        BigDecimal totalAmount,
        String paymentMethod,
        String paymentStatus,
        String orderStatus,
        LocalDateTime createdAt,
        List<OrderResponse.OrderItemResponse> items
) {
}
