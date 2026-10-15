
package com.example.analytics.controller;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final DatabaseClient databaseClient;

    public AnalyticsController(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of(
                "status", "UP",
                "application", "Advanced Real-time Analytics Dashboard",
                "message", "Analytics backend is running successfully"
        );
    }

    @GetMapping("/overview")
    public Mono<Map<String, Object>> getOverview() {

        // All active accounts, including administrators.
        Mono<Long> totalUsers = databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM customer_accounts
                WHERE active = TRUE
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // Sessions active within the last 30 minutes.
        Mono<Long> activeSessions = databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM public.sessions
                WHERE is_active = TRUE
                  AND last_seen_at >=
                      CURRENT_TIMESTAMP - INTERVAL '30 minutes'
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // Distinct users with a recently active session.
        Mono<Long> activeUsers = databaseClient.sql("""
                SELECT COUNT(DISTINCT user_id) AS total
                FROM public.sessions
                WHERE is_active = TRUE
                  AND last_seen_at >=
                      CURRENT_TIMESTAMP - INTERVAL '30 minutes'
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // Total recorded page views.
        Mono<Long> pageViews = databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM analytics_events
                WHERE event_type = 'PAGE_VIEW'
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // Total recorded analytics events.
        Mono<Long> totalEvents = databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM analytics_events
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // Active customers, excluding administrators.
        Mono<Long> totalCustomers = databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM customer_accounts
                WHERE active = TRUE
                  AND role = 'CUSTOMER'
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // All orders.
        Mono<Long> totalOrders = databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM public.customer_orders
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // Total products in the MAISON store.
        Mono<Long> totalProducts = databaseClient.sql("""
                SELECT COUNT(*) AS total
                FROM public.products
                """)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        // Revenue from successful, non-cancelled orders.
        Mono<BigDecimal> totalRevenue = databaseClient.sql("""
                SELECT COALESCE(SUM(total_amount), 0) AS revenue
                FROM public.customer_orders
                WHERE UPPER(payment_status) = 'SUCCESS'
                  AND UPPER(order_status) <> 'CANCELLED'
                """)
                .map((row, metadata) ->
                        row.get("revenue", BigDecimal.class))
                .one()
                .defaultIfEmpty(BigDecimal.ZERO);

        // Combine all nine database results.
        // The array-based overload supports more than eight publishers.
        return Mono.zip(
        Mono.zip(
                totalUsers,
                activeUsers,
                activeSessions,
                pageViews,
                totalEvents,
                totalCustomers,
                totalOrders,
                totalRevenue
        ),
        totalProducts
).map(result -> {
    var metrics = result.getT1();
    Map<String, Object> overview = new LinkedHashMap<>();

    overview.put("totalUsers", metrics.getT1());
    overview.put("activeUsers", metrics.getT2());
    overview.put("activeSessions", metrics.getT3());
    overview.put("pageViews", metrics.getT4());
    overview.put("totalEvents", metrics.getT5());
    overview.put("totalCustomers", metrics.getT6());
    overview.put("totalOrders", metrics.getT7());
    overview.put("totalRevenue", metrics.getT8());
    overview.put("totalProducts", result.getT2());

    return overview;
});
    }

    @GetMapping("/revenue-trend")
    public Flux<Map<String, Object>> getRevenueTrend(
            @RequestParam(defaultValue = "7") int days) {

        int safeDays = (days == 30) ? 30 : 7;

        return databaseClient.sql("""
                WITH date_series AS (
                    SELECT generate_series(
                        CURRENT_DATE - ((:days - 1) * INTERVAL '1 day'),
                        CURRENT_DATE,
                        INTERVAL '1 day'
                    )::date AS revenue_date
                ),
                daily_revenue AS (
                    SELECT
                        created_at::date AS revenue_date,
                        SUM(total_amount) AS revenue
                    FROM public.customer_orders
                    WHERE UPPER(payment_status) = 'SUCCESS'
                      AND UPPER(order_status) <> 'CANCELLED'
                      AND created_at >=
                          CURRENT_DATE - ((:days - 1) * INTERVAL '1 day')
                      AND created_at < CURRENT_DATE + INTERVAL '1 day'
                    GROUP BY created_at::date
                )
                SELECT
                    TO_CHAR(ds.revenue_date, 'DD Mon') AS date,
                    COALESCE(dr.revenue, 0) AS revenue
                FROM date_series ds
                LEFT JOIN daily_revenue dr
                    ON ds.revenue_date = dr.revenue_date
                ORDER BY ds.revenue_date
                """)
                .bind("days", safeDays)
                .map((row, metadata) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", row.get("date", String.class));
                    item.put("revenue", row.get("revenue", BigDecimal.class));
                    return item;
                })
                .all();
    }

    @GetMapping("/events")
    public Flux<Map<String, Object>> getRecentEvents() {

        return databaseClient.sql("""
                SELECT
                    e.id,
                    e.event_type,
                    e.page_url,
                    e.created_at,
                    ca.full_name AS user_name
                FROM analytics_events e
                LEFT JOIN customer_accounts ca
                    ON e.user_id = ca.id
                ORDER BY e.created_at DESC
                LIMIT 20
                """)
                .map((row, metadata) -> {
                    Map<String, Object> event = new LinkedHashMap<>();

                    event.put("id", row.get("id"));
                    event.put("eventType", row.get("event_type"));
                    event.put("pageUrl", row.get("page_url"));
                    event.put("createdAt", row.get("created_at"));
                    event.put("userName", row.get("user_name"));

                    return event;
                })
                .all();
    }

    @GetMapping("/users")
    public Flux<Map<String, Object>> getUsers() {

        return databaseClient.sql("""
                SELECT
                    id,
                    full_name,
                    email,
                    role,
                    active,
                    created_at
                FROM customer_accounts
                ORDER BY created_at DESC NULLS LAST, id DESC
                """)
                .map((row, metadata) -> {
                    Map<String, Object> user = new LinkedHashMap<>();

                    user.put("id", row.get("id"));
                    user.put("fullName", row.get("full_name"));
                    user.put("email", row.get("email"));
                    user.put("role", row.get("role"));
                    user.put("active", row.get("active"));
                    user.put("createdAt", row.get("created_at"));

                    return user;
                })
                .all();
    }
}
