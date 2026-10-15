package com.example.analytics.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.analytics.dto.OrderResponse;
import com.example.analytics.dto.PlaceOrderRequest;
import com.example.analytics.service.OrderService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Mono<OrderResponse> placeOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PlaceOrderRequest request) {

        Long userId = getUserId(jwt);

        return orderService.placeOrder(userId, request);
    }

    @GetMapping
    public Mono<List<OrderResponse>> getOrders(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        return orderService.getOrders(userId);
    }

    private Long getUserId(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please login first."
            );
        }

        Object value = jwt.getClaim("userId");

        if (!(value instanceof Number number)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid login token."
            );
        }

        long userId = number.longValue();

        if (userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid login token."
            );
        }

        return userId;
    }
}
