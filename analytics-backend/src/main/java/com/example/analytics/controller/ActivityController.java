
package com.example.analytics.controller;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final DatabaseClient databaseClient;

    private static final Set<String> ALLOWED_EVENTS = Set.of(
        "PAGE_VIEW",
        "PRODUCT_VIEW",
        "ADD_TO_CART",
        "BUY_NOW",
        "CHECKOUT_STARTED",
        "ORDER_PLACED",
        "LOGOUT",
        "HEARTBEAT"
);
    public ActivityController(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public record ActivityRequest(
            String eventType,
            String pageUrl
    ) {}

    @PostMapping("/events")
    public Mono<Map<String, String>> recordActivity(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ActivityRequest request) {

        if (jwt == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            ));
        }

        if (request == null || request.eventType() == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "eventType is required"
            ));
        }

        String eventType = request.eventType()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!ALLOWED_EVENTS.contains(eventType)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported event type"
            ));
        }

        String pageUrl = request.pageUrl() == null
                ? "/"
                : request.pageUrl().trim();

        if (pageUrl.isEmpty()) {
            pageUrl = "/";
        }

        if (pageUrl.length() > 500) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "pageUrl is too long"
            ));
        }

        String email = jwt.getSubject();
       Object rawSessionId = jwt.getClaims().get("sessionId");

Long sessionId = null;

if (rawSessionId instanceof Number number) {
    sessionId = number.longValue();
}

        if (email == null || email.isBlank() || sessionId == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "JWT is missing email or sessionId. Please log in again."
            ));
        }

        final String customerEmail = email;
        final String eventPageUrl = pageUrl;
        final Long tokenSessionId = sessionId;

        return databaseClient.sql("""
                SELECT id
                FROM public.customer_accounts
                WHERE LOWER(email) = LOWER(:email)
                  AND active = TRUE
                LIMIT 1
                """)
                .bind("email", customerEmail)
                .map((row, metadata) -> row.get("id", Long.class))
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Customer account not found for JWT identity"
                        )
                ))
                .flatMap(userId ->
                        databaseClient.sql("""
                                SELECT id
                                FROM public.sessions
                                WHERE id = :sessionId
                                  AND user_id = :userId
                                  AND is_active = TRUE
                                LIMIT 1
                                """)
                                .bind("sessionId", tokenSessionId)
                                .bind("userId", userId)
                                .map((row, metadata) ->
                                        row.get("id", Long.class))
                                .one()
                                .switchIfEmpty(Mono.error(
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED,
                                                "Session is no longer active. Please log in again."
                                        )
                                ))
        .flatMap(validSessionId -> {
    if (eventType.equals("LOGOUT")) {
        return recordEvent(userId, eventType, eventPageUrl)
                .then(closeSession(userId, validSessionId))
                .thenReturn(Map.of(
                        "status", "success",
                        "eventType", eventType
                ));
    }

    if (eventType.equals("HEARTBEAT")) {
        return updateLastSeen(userId, validSessionId)
                .thenReturn(Map.of(
                        "status", "success",
                        "eventType", eventType
                ));
    }

    return updateLastSeen(userId, validSessionId)
            .then(recordEvent(userId, eventType, eventPageUrl))
            .thenReturn(Map.of(
                    "status", "success",
                    "eventType", eventType
            ));
})
                                .thenReturn(Map.of(
                                        "status", "success",
                                        "eventType", eventType
                                ))
                );
    }

    private Mono<Void> recordEvent(
            Long userId,
            String eventType,
            String pageUrl) {

        return databaseClient.sql("""
                INSERT INTO public.analytics_events
                    (user_id, event_type, page_url, created_at)
                VALUES
                    (:userId, :eventType, :pageUrl, CURRENT_TIMESTAMP)
                """)
                .bind("userId", userId)
                .bind("eventType", eventType)
                .bind("pageUrl", pageUrl)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> updateLastSeen(
            Long userId,
            Long sessionId) {

        return databaseClient.sql("""
                UPDATE public.sessions
                SET last_seen_at = CURRENT_TIMESTAMP
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND is_active = TRUE
                """)
                .bind("sessionId", sessionId)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> closeSession(
            Long userId,
            Long sessionId) {

        return databaseClient.sql("""
                UPDATE public.sessions
                SET is_active = FALSE,
                    ended_at = CURRENT_TIMESTAMP
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND is_active = TRUE
                """)
                .bind("sessionId", sessionId)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
