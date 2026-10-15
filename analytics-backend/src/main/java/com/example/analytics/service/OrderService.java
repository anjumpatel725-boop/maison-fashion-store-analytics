
package com.example.analytics.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import com.example.analytics.dto.OrderResponse;
import com.example.analytics.dto.PlaceOrderRequest;
import com.example.analytics.dto.PlaceOrderRequest.OrderItemRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OrderService {

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public OrderService(
            DatabaseClient databaseClient,
            TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    private record ProductData(
            Long id,
            String name,
            String sku,
            BigDecimal price,
            int quantity) {}

    private record SavedOrder(
            Long id,
            String orderNumber,
            BigDecimal totalAmount,
            LocalDateTime createdAt) {}

    public Mono<OrderResponse> placeOrder(
            Long customerId,
            PlaceOrderRequest request) {

        if (customerId == null || customerId <= 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid customer"));
        }

        if (request.items().size() > 50) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Too many order items"));
        }

        Mono<OrderResponse> operation = Mono.defer(() ->
                Flux.fromIterable(request.items())
                        .concatMap(item -> getProduct(item))
                        .collectList()
                        .flatMap(products -> {

                            BigDecimal total = products.stream()
                                    .map(p -> p.price()
                                            .multiply(BigDecimal.valueOf(
                                                    p.quantity())))
                                    .reduce(BigDecimal.ZERO,
                                            BigDecimal::add);

                            String orderNumber = "MSN-"
                                    + UUID.randomUUID()
                                            .toString()
                                            .substring(0, 12)
                                            .toUpperCase();

                            return databaseClient.sql("""
                                    INSERT INTO public.customer_orders
                                        (customer_id, order_number,
                                         total_amount, payment_method,
                                         payment_status, order_status)
                                    VALUES
                                        (:customerId, :orderNumber,
                                         :total, :paymentMethod,
                                         'SUCCESS', 'PLACED')
                                    RETURNING id, order_number,
                                              total_amount, created_at
                                    """)
                                    .bind("customerId", customerId)
                                    .bind("orderNumber", orderNumber)
                                    .bind("total", total)
                                    .bind("paymentMethod",
                                            request.paymentMethod())
                                    .map((row, metadata) ->
                                            new SavedOrder(
                                                    row.get("id", Long.class),
                                                    row.get("order_number",
                                                            String.class),
                                                    row.get("total_amount",
                                                            BigDecimal.class),
                                                    row.get("created_at",
                                                            LocalDateTime.class)
                                            ))
                                    .one()
                                    .flatMap(saved -> saveItems(
                                            saved.id(), products)
                                            .thenReturn(
                                                    new OrderResponse(
                                                            saved.id(),
                                                            saved.orderNumber(),
                                                            saved.totalAmount(),
                                                            request.paymentMethod(),
                                                            "SUCCESS",
                                                            "PLACED",
                                                            saved.createdAt(),
                                                            products.stream()
                                                                    .map(p ->
                                                                            new OrderResponse
                                                                            .OrderItemResponse(
                                                                                    p.id(),
                                                                                    p.name(),
                                                                                    p.sku(),
                                                                                    p.price(),
                                                                                    p.quantity(),
                                                                                    p.price().multiply(
                                                                                            BigDecimal.valueOf(
                                                                                                    p.quantity()))
                                                                            ))
                                                                    .toList()
                                                    )));
                        })
        );

        return transactionalOperator.transactional(operation);
    }

    private Mono<ProductData> getProduct(OrderItemRequest item) {

        if (item.productId() == null
                || item.productId() <= 0
                || item.quantity() < 1) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid product or quantity"));
        }

        return databaseClient.sql("""
                SELECT id, name, sku, price, stock
                FROM public.products
                WHERE id = :id AND active = TRUE
                """)
                .bind("id", item.productId())
                .map((row, metadata) -> new Object[] {
                        row.get("id", Long.class),
                        row.get("name", String.class),
                        row.get("sku", String.class),
                        row.get("price", BigDecimal.class),
                        row.get("stock", Integer.class)
                })
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product not found or inactive: "
                                + item.productId())))
                .flatMap(data -> {
                    Long id = (Long) data[0];
                    String name = (String) data[1];
                    String sku = (String) data[2];
                    BigDecimal price = (BigDecimal) data[3];
                    Integer stock = (Integer) data[4];

                    if (stock == null || stock < item.quantity()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Insufficient stock for " + name));
                    }

                    return Mono.just(new ProductData(
                            id, name, sku, price, item.quantity()));
                });
    }

    private Mono<Void> saveItems(
            Long orderId, List<ProductData> products) {

        return Flux.fromIterable(products)
                .concatMap(product ->
                        databaseClient.sql("""
                                INSERT INTO public.customer_order_items
                                    (order_id, product_id, product_name,
                                     sku, unit_price, quantity, line_total)
                                VALUES
                                    (:orderId, :productId, :productName,
                                     :sku, :unitPrice, :quantity, :lineTotal)
                                """)
                                .bind("orderId", orderId)
                                .bind("productId", product.id())
                                .bind("productName", product.name())
                                .bind("sku", product.sku())
                                .bind("unitPrice", product.price())
                                .bind("quantity", product.quantity())
                                .bind("lineTotal",
                                        product.price().multiply(
                                                BigDecimal.valueOf(
                                                        product.quantity())))
                                .fetch()
                                .rowsUpdated()
                )
                .then();
    }

    public Mono<List<OrderResponse>> getOrders(Long customerId) {

        return databaseClient.sql("""
                SELECT id, order_number, total_amount, payment_method,
                       payment_status, order_status, created_at
                FROM public.customer_orders
                WHERE customer_id = :customerId
                ORDER BY created_at DESC
                """)
                .bind("customerId", customerId)
                .map((row, metadata) -> new OrderResponse(
                        row.get("id", Long.class),
                        row.get("order_number", String.class),
                        row.get("total_amount", BigDecimal.class),
                        row.get("payment_method", String.class),
                        row.get("payment_status", String.class),
                        row.get("order_status", String.class),
                        row.get("created_at", LocalDateTime.class),
                        List.of()
                ))
                .all()
                .concatMap(order ->
                        databaseClient.sql("""
                                SELECT product_id, product_name, sku,
                                       unit_price, quantity, line_total
                                FROM public.customer_order_items
                                WHERE order_id = :orderId
                                ORDER BY id
                                """)
                                .bind("orderId", order.id())
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
                                .collectList()
                                .map(items -> new OrderResponse(
                                        order.id(),
                                        order.orderNumber(),
                                        order.totalAmount(),
                                        order.paymentMethod(),
                                        order.paymentStatus(),
                                        order.orderStatus(),
                                        order.createdAt(),
                                        items
                                ))
                )
                .collectList();
    }
}
