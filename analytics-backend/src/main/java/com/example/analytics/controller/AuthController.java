package com.example.analytics.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.analytics.dto.AuthResponse;
import com.example.analytics.dto.LoginRequest;
import com.example.analytics.dto.RegisterRequest;
import com.example.analytics.service.AuthService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        return authService.register(request)
                .map(response -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(request)
                .map(ResponseEntity::ok);
    }
}
