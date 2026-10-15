
package com.example.analytics.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final DatabaseClient databaseClient;

    public ProductController(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    // Public: active products only
    @GetMapping
    public Flux<Product> getAllProducts() {
        return databaseClient.sql("""
                SELECT id, sku, name, description, category,
                       price, stock, image_url, active, created_at
                FROM public.products
                WHERE active = TRUE
                ORDER BY id
                """)
                .map((row, metadata) -> mapProduct(row))
                .all();
    }

    // Admin: all products, including inactive products
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<Product> getAllProductsForAdmin() {
        return databaseClient.sql("""
                SELECT id, sku, name, description, category,
                       price, stock, image_url, active, created_at
                FROM public.products
                ORDER BY id DESC
                """)
                .map((row, metadata) -> mapProduct(row))
                .all();
    }

    // Admin: create a product
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Product> createProduct(
            @Valid @RequestBody ProductRequest request) {

        return databaseClient.sql("""
                INSERT INTO public.products
                    (sku, name, description, category, price,
                     stock, image_url, active)
                VALUES
                    (:sku, :name, :description, :category, :price,
                     :stock, :imageUrl, TRUE)
                RETURNING id, sku, name, description, category,
                          price, stock, image_url, active, created_at
                """)
                .bind("sku", request.sku().trim())
                .bind("name", request.name().trim())
                .bind("description",
                        request.description() == null
                                ? "" : request.description().trim())
                .bind("category", request.category().trim())
                .bind("price", request.price())
                .bind("stock", request.stock())
                .bind("imageUrl",
                        request.imageUrl() == null
                                ? "" : request.imageUrl().trim())
                .map((row, metadata) -> mapProduct(row))
                .one()
                .onErrorMap(
                        org.springframework.dao.DataIntegrityViolationException.class,
                        ex -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Product SKU may already exist or violate a database constraint"
                        )
                );
    }

    // Admin: update a product
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Product> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        return databaseClient.sql("""
                UPDATE public.products
                SET sku = :sku,
                    name = :name,
                    description = :description,
                    category = :category,
                    price = :price,
                    stock = :stock,
                    image_url = :imageUrl
                WHERE id = :id
                RETURNING id, sku, name, description, category,
                          price, stock, image_url, active, created_at
                """)
                .bind("id", id)
                .bind("sku", request.sku().trim())
                .bind("name", request.name().trim())
                .bind("description",
                        request.description() == null
                                ? "" : request.description().trim())
                .bind("category", request.category().trim())
                .bind("price", request.price())
                .bind("stock", request.stock())
                .bind("imageUrl",
                        request.imageUrl() == null
                                ? "" : request.imageUrl().trim())
                .map((row, metadata) -> mapProduct(row))
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product not found"
                        )
                ));
    }

    // Admin: deactivate a product instead of permanently deleting it
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Product> deactivateProduct(@PathVariable Long id) {

        return databaseClient.sql("""
                UPDATE public.products
                SET active = FALSE
                WHERE id = :id
                RETURNING id, sku, name, description, category,
                          price, stock, image_url, active, created_at
                """)
                .bind("id", id)
                .map((row, metadata) -> mapProduct(row))
                .one()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product not found"
                        )
                ));
    }

    private Product mapProduct(
            io.r2dbc.spi.Row row) {

        return new Product(
                row.get("id", Long.class),
                row.get("sku", String.class),
                row.get("name", String.class),
                row.get("description", String.class),
                row.get("category", String.class),
                row.get("price", BigDecimal.class),
                row.get("stock", Integer.class),
                row.get("image_url", String.class),
                row.get("active", Boolean.class),
                row.get("created_at", LocalDateTime.class)
        );
    }

    public record ProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            String description,
            @NotBlank String category,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotNull @Min(0) Integer stock,
            String imageUrl
    ) {}

    public record Product(
            Long id,
            String sku,
            String name,
            String description,
            String category,
            BigDecimal price,
            Integer stock,
            String imageUrl,
            Boolean active,
            LocalDateTime createdAt
    ) {}
}
