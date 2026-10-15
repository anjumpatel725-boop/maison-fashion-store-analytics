package com.example.analytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class SessionCleanupService {

    private static final Logger log =
            LoggerFactory.getLogger(SessionCleanupService.class);

    private final DatabaseClient databaseClient;

    public SessionCleanupService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupStaleSessions() {
        databaseClient.sql("""
                UPDATE public.sessions
                SET is_active = FALSE,
                    ended_at = COALESCE(ended_at, CURRENT_TIMESTAMP)
                WHERE is_active = TRUE
                  AND last_seen_at < CURRENT_TIMESTAMP
                      - INTERVAL '30 minutes'
                """)
                .fetch()
                .rowsUpdated()
                .doOnNext(count -> {
                    if (count > 0) {
                        log.info(
                                "Closed {} stale session(s)",
                                count
                        );
                    }
                })
                .onErrorResume(error -> {
                    log.error(
                            "Failed to clean up stale sessions",
                            error
                    );
                    return Mono.empty();
                })
                .subscribe();
    }
}
