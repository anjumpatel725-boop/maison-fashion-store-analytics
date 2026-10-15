
package com.example.analytics.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.analytics.dto.AuthResponse;
import com.example.analytics.dto.LoginRequest;
import com.example.analytics.dto.RegisterRequest;
import com.example.analytics.model.CustomerAccount;

import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final DatabaseClient databaseClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            DatabaseClient databaseClient,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.databaseClient = databaseClient;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // =========================
    // REGISTER
    // =========================
    public Mono<AuthResponse> register(RegisterRequest request) {

        String fullName = request.fullName().trim();
        String email = normalizeEmail(request.email());
        String password = request.password();

        if (fullName.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Full name is required"
            ));
        }

        if (password.length() < 8 || password.length() > 72) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be between 8 and 72 characters"
            ));
        }

        String passwordHash = passwordEncoder.encode(password);

        return databaseClient.sql("""
                INSERT INTO public.customer_accounts
                    (full_name, email, password_hash, role, active)
                VALUES
                    (:fullName, :email, :passwordHash, 'CUSTOMER', TRUE)
                ON CONFLICT (email) DO NOTHING
                RETURNING id, full_name, email, password_hash,
                          role, active, created_at
                """)
                .bind("fullName", fullName)
                .bind("email", email)
                .bind("passwordHash", passwordHash)
                .map((row, metadata) -> new CustomerAccount(
                        row.get("id", Long.class),
                        row.get("full_name", String.class),
                        row.get("email", String.class),
                        row.get("password_hash", String.class),
                        row.get("role", String.class),
                        row.get("active", Boolean.class),
                        row.get("created_at", LocalDateTime.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "An account with this email already exists"
                        )
                ))
                .flatMap(customer ->
                        createSession(customer)
                                .map(sessionId ->
                                        createAuthResponse(customer, sessionId)
                                )
                );
    }

    // =========================
    // LOGIN
    // =========================
    public Mono<AuthResponse> login(LoginRequest request) {

        String email = normalizeEmail(request.email());

        return databaseClient.sql("""
                SELECT id, full_name, email, password_hash,
                       role, active, created_at
                FROM public.customer_accounts
                WHERE email = :email
                LIMIT 1
                """)
                .bind("email", email)
                .map((row, metadata) -> new CustomerAccount(
                        row.get("id", Long.class),
                        row.get("full_name", String.class),
                        row.get("email", String.class),
                        row.get("password_hash", String.class),
                        row.get("role", String.class),
                        row.get("active", Boolean.class),
                        row.get("created_at", LocalDateTime.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Account not found. Please register first."
                        )
                ))
                .flatMap(customer -> {

                    if (!Boolean.TRUE.equals(customer.active())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Your account is inactive. Please contact support."
                        ));
                    }

                    if (!passwordEncoder.matches(
                            request.password(),
                            customer.passwordHash())) {

                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid password. Please try again."
                        ));
                    }

                    return recordLoginEvent(customer)
                            .onErrorResume(error -> {
                                System.err.println(
                                        "Failed to record login event: "
                                                + error.getMessage()
                                );
                                // Analytics event failure should not block login.
                                return Mono.empty();
                            })
                            .then(createSession(customer))
                            .map(sessionId ->
                                    createAuthResponse(customer, sessionId)
                            );
                });
    }

    // =========================
    // RECORD LOGIN EVENT
    // =========================
    private Mono<Void> recordLoginEvent(CustomerAccount customer) {

        return databaseClient.sql("""
                INSERT INTO public.analytics_events
                    (user_id, event_type, page_url, created_at)
                VALUES
                    (:userId, 'LOGIN', '/login', CURRENT_TIMESTAMP)
                """)
                .bind("userId", customer.id())
                .fetch()
                .rowsUpdated()
                .then();
    }

    // =========================
    // CREATE SESSION
    // =========================
    private Mono<Long> createSession(CustomerAccount customer) {

        return databaseClient.sql("""
                INSERT INTO public.sessions
                    (user_id, started_at, last_seen_at, ended_at, is_active)
                VALUES
                    (:userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, TRUE)
                RETURNING id
                """)
                .bind("userId", customer.id())
                .map((row, metadata) -> row.get("id", Long.class))
                .one();
    }

    // =========================
    // CREATE AUTH RESPONSE
    // =========================
    private AuthResponse createAuthResponse(
            CustomerAccount customer,
            Long sessionId) {

        return new AuthResponse(
                jwtService.generateToken(customer, sessionId),
                "Bearer",
                customer.id(),
                customer.fullName(),
                customer.email(),
                customer.role()
        );
    }

    // =========================
    // NORMALIZE EMAIL
    // =========================
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
