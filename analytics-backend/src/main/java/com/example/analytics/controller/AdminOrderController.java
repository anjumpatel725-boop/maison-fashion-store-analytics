package com.example.analytics.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.analytics.dto.AdminOrderResponse;
import com.example.analytics.dto.OrderResponse;
import com.example.analytics.dto.OrderStatusUpdateRequest;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/orders")
@Validated
public class AdminOrderController {

    private final DatabaseClient databaseClient;

    public AdminOrderController(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @GetMapping("/count")
    public Mono<Long> getTotalOrders() {
        return databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM public.customer_orders
                """)
                .map((row, metadata) ->
                        row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @GetMapping
    public Mono<List<AdminOrderResponse>> getAllOrders() {

        return databaseClient.sql("""
                SELECT
                    o.id,
                    o.order_number,
                    o.customer_id,
                    COALESCE(
                        o.customer_name,
                        a.full_name,
                        'Customer'
                    ) AS customer_name,
                    COALESCE(
                        o.customer_email,
                        a.email,
                        ''
                    ) AS customer_email,
                    o.total_amount,
                    o.payment_method,
                    o.payment_status,
                    o.order_status,
                    o.created_at
                FROM public.customer_orders o
                LEFT JOIN public.customer_accounts a
                    ON a.id = o.customer_id
                ORDER BY o.created_at DESC, o.id DESC
                """)
                .map((row, metadata) ->
                        new AdminOrderResponse(
                                row.get("id", Long.class),
                                row.get("order_number", String.class),
                                row.get("customer_id", Long.class),
                                row.get("customer_name", String.class),
                                row.get("customer_email", String.class),
                                row.get("total_amount", BigDecimal.class),
                                row.get("payment_method", String.class),
                                row.get("payment_status", String.class),
                                row.get("order_status", String.class),
                                row.get("created_at", LocalDateTime.class),
                                List.of()
                        ))
                .all()
                .concatMap(order ->
                        getOrderItems(order.id())
                                .map(items -> new AdminOrderResponse(
                                        order.id(),
                                        order.orderNumber(),
                                        order.customerId(),
                                        order.customerName(),
                                        order.customerEmail(),
                                        order.totalAmount(),
                                        order.paymentMethod(),
                                        order.paymentStatus(),
                                        order.orderStatus(),
                                        order.createdAt(),
                                        items
                                )))
                .collectList();
    }

    private Mono<List<OrderResponse.OrderItemResponse>> getOrderItems(
            Long orderId) {

        return databaseClient.sql("""
                SELECT
                    product_id,
                    product_name,
                    sku,
                    unit_price,
                    quantity,
                    line_total
                FROM public.customer_order_items
                WHERE order_id = :orderId
                ORDER BY id
                """)
                .bind("orderId", orderId)
                .map((row, metadata) ->
                        new OrderResponse.OrderItemResponse(
                                row.get("product_id", Long.class),
                                row.get("product_name", String.class),
                                row.get("sku", String.class),
                                row.get("unit_price", BigDecimal.class),
                                row.get("quantity", Integer.class),
                                row.get("line_total", BigDecimal.class)
                        ))
                .all()
                .collectList();
    }

    @PatchMapping("/{orderId}/status")
    public Mono<AdminOrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {

        if (orderId <= 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid order ID"
            ));
        }

        return databaseClient.sql("""
                UPDATE public.customer_orders
                SET order_status = :status,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :orderId
                RETURNING id
                """)
                .bind("status", request.orderStatus())
                .bind("orderId", orderId)
                .map((row, metadata) ->
                        row.get("id", Long.class))
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found"
                        )))
                .then(getOrderById(orderId));
    }

    private Mono<AdminOrderResponse> getOrderById(Long orderId) {

        return databaseClient.sql("""
                SELECT
                    o.id,
                    o.order_number,
                    o.customer_id,
                    COALESCE(
                        o.customer_name,
                        a.full_name,
                        'Customer'
                    ) AS customer_name,
                    COALESCE(
                        o.customer_email,
                        a.email,
                        ''
                    ) AS customer_email,
                    o.total_amount,
                    o.payment_method,
                    o.payment_status,
                    o.order_status,
                    o.created_at
                FROM public.customer_orders o
                LEFT JOIN public.customer_accounts a
                    ON a.id = o.customer_id
                WHERE o.id = :orderId
                """)
                .bind("orderId", orderId)
                .map((row, metadata) ->
                        new AdminOrderResponse(
                                row.get("id", Long.class),
                                row.get("order_number", String.class),
                                row.get("customer_id", Long.class),
                                row.get("customer_name", String.class),
                                row.get("customer_email", String.class),
                                row.get("total_amount", BigDecimal.class),
                                row.get("payment_method", String.class),
                                row.get("payment_status", String.class),
                                row.get("order_status", String.class),
                                row.get("created_at", LocalDateTime.class),
                                List.of()
                        ))
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found"
                        )))
                .flatMap(order ->
                        getOrderItems(order.id())
                                .map(items -> new AdminOrderResponse(
                                        order.id(),
                                        order.orderNumber(),
                                        order.customerId(),
                                        order.customerName(),
                                        order.customerEmail(),
                                        order.totalAmount(),
                                        order.paymentMethod(),
                                        order.paymentStatus(),
                                        order.orderStatus(),
                                        order.createdAt(),
                                        items
                                )));
    }
}
