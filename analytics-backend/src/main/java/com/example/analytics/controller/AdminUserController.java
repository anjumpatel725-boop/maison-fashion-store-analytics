
package com.example.analytics.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final DatabaseClient databaseClient;

    public AdminUserController(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @GetMapping("/count")
    public Mono<ResponseEntity<Map<String, Long>>> getTotalUsers(
            @AuthenticationPrincipal Jwt jwt) {

        // Only ADMIN can access this endpoint.
        if (jwt == null ||
                !"ADMIN".equals(jwt.getClaimAsString("role"))) {

            return Mono.just(
                ResponseEntity
                    .<Map<String, Long>>status(HttpStatus.FORBIDDEN)
                    .build()
            );
        }

        return databaseClient
            .sql("""
                SELECT COUNT(*) AS total_users
                FROM public.customer_accounts
                WHERE role = 'CUSTOMER'
                """)
            .map((row, metadata) ->
                row.get("total_users", Long.class)
            )
            .one()
            .defaultIfEmpty(0L)
            .map(count ->
                ResponseEntity.ok(
                    Map.of("totalUsers", count)
                )
            );
    }
    
@GetMapping("/all")
public Mono<ResponseEntity<?>> getAllUsers(
        @AuthenticationPrincipal Jwt jwt) {

    if (jwt == null ||
            !"ADMIN".equals(jwt.getClaimAsString("role"))) {
        return Mono.just(
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        );
    }

    return databaseClient.sql("""
        SELECT id, full_name, email, role
        FROM public.customer_accounts
        WHERE role = 'CUSTOMER'
        ORDER BY id DESC
        """)
        .map((row, metadata) -> Map.of(
            "id", row.get("id", Long.class),
            "fullName", row.get("full_name", String.class),
            "email", row.get("email", String.class),
            "role", row.get("role", String.class)
        ))
        .all()
        .collectList()
        .map(users -> ResponseEntity.ok(users));
}

}
