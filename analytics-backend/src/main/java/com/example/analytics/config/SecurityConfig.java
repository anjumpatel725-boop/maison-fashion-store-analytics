package com.example.analytics.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174"
        ));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin"
        ));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>>
            jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String role = jwt.getClaimAsString("role");

            if (role == null || role.isBlank()) {
                return List.of();
            }

            String normalizedRole =
                    role.toUpperCase(Locale.ROOT);

            if (normalizedRole.startsWith("ROLE_")) {
                normalizedRole =
                        normalizedRole.substring("ROLE_".length());
            }

            return List.of(
                    new SimpleGrantedAuthority(
                            "ROLE_" + normalizedRole
                    )
            );
        });

        return new ReactiveJwtAuthenticationConverterAdapter(
                converter
        );
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http) {

        return http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))

                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .httpBasic(
                        ServerHttpSecurity.HttpBasicSpec::disable
                )

                .formLogin(
                        ServerHttpSecurity.FormLoginSpec::disable
                )

                .authorizeExchange(exchange -> exchange

                        // Public authentication and health endpoints
                        .pathMatchers(
                                "/api/auth/**",
                                "/actuator/health"
                        ).permitAll()

                      // Authenticated customers can record their own activity
.pathMatchers("/api/activity/**").authenticated()

// Analytics dashboard: ADMIN and HR_ADMIN only
.pathMatchers("/api/analytics/**").hasAnyRole("ADMIN", "HR_ADMIN")
                        // Admin-only order management
                        .pathMatchers(
                                "/api/admin/orders/**"
                        ).hasRole("ADMIN")

                        // Authenticated customer order APIs
                        .pathMatchers(
                                "/api/orders/**"
                        ).authenticated()

                        // Admin-only product listing
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/products/admin/**"
                        ).hasRole("ADMIN")

                        // Public product listing and details
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/products",
                                "/api/products/"
                        ).permitAll()

                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/products/**"
                        ).permitAll()

                        // Admin-only product creation
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/products",
                                "/api/products/**"
                        ).hasRole("ADMIN")

                        // Admin-only product updates
                        .pathMatchers(
                                HttpMethod.PUT,
                                "/api/products",
                                "/api/products/**"
                        ).hasRole("ADMIN")

                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/api/products",
                                "/api/products/**"
                        ).hasRole("ADMIN")

                        // Admin-only product deletion
                        .pathMatchers(
                                HttpMethod.DELETE,
                                "/api/products",
                                "/api/products/**"
                        ).hasRole("ADMIN")

                        // Admin-only registered-user APIs
                        .pathMatchers(
                                "/api/admin/users/**"
                        ).hasRole("ADMIN")

                        // All other endpoints require authentication
                        .anyExchange().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter()
                        ))
                )

                .build();
    }
}
