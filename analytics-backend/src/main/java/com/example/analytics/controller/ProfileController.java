package com.example.analytics.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final DatabaseClient databaseClient;

    public ProfileController(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @GetMapping
    public Mono<Map<String, Object>> getProfile(
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            ));
        }

        Long userId = jwt.getClaim("userId");

        if (userId == null || userId <= 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication token."
            ));
        }

        return databaseClient.sql("""
                SELECT id, full_name, email, role, created_at
                FROM public.customer_accounts
                WHERE id = :id
                """)
                .bind("id", userId)
                .map((row, metadata) -> {

                    Map<String, Object> profile =
                            new LinkedHashMap<>();

                    profile.put(
                            "id",
                            row.get("id", Long.class)
                    );

                    profile.put(
                            "fullName",
                            row.get("full_name", String.class)
                    );

                    profile.put(
                            "email",
                            row.get("email", String.class)
                    );

                    profile.put(
                            "role",
                            row.get("role", String.class)
                    );

                    profile.put(
                            "createdAt",
                            row.get(
                                    "created_at",
                                    LocalDateTime.class
                            )
                    );

                    return profile;
                })
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Profile not found."
                        )
                ));
    }
}
