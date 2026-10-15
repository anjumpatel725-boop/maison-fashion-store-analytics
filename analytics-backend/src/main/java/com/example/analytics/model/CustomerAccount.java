package com.example.analytics.model;

import java.time.LocalDateTime;

public record CustomerAccount(
        Long id,
        String fullName,
        String email,
        String passwordHash,
        String role,
        Boolean active,
        LocalDateTime createdAt
) {}
