package com.example.otel.controller;

import com.example.otel.model.Order;
import com.example.otel.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST API for the Order resource.
 *
 * Spring MVC + Micrometer auto-instrumentation creates an HTTP server span
 * for every incoming request and records http_server_requests_seconds metrics
 * (count, sum, histogram buckets) tagged by method / uri / status.
 *
 * Demo endpoints (/demo/*) are deliberately pathological so you can generate
 * interesting trace shapes, log lines, and metric spikes in Grafana.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ─── Core CRUD ───────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest req) {
        log.info("POST /orders customerId={} product={}", req.customerId(), req.product());
        Order order = orderService.createOrder(req.customerId(), req.product(), req.amount());
        return ResponseEntity.status(201).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return orderService.findOrder(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Order> listOrders() {
        return orderService.listOrders();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Order> cancelOrder(@PathVariable String id) {
        try {
            return ResponseEntity.ok(orderService.cancelOrder(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ─── Demo / Observability test endpoints ─────────────────────────────────

    /**
     * Simulates a slow dependency call.
     * Watch the p99 latency panel in Grafana spike after hitting this endpoint.
     */
    @GetMapping("/demo/slow")
    public ResponseEntity<String> slowEndpoint() throws InterruptedException {
        log.warn("Slow endpoint called — simulating a 2-second downstream delay");
        Thread.sleep(2_000);
        return ResponseEntity.ok("Slow response completed after 2s");
    }

    /**
     * Throws an unhandled exception.
     * Produces a 500 in http_server_requests_seconds{status="500"} and
     * a red error span in Tempo.
     */
    @GetMapping("/demo/error")
    public ResponseEntity<String> errorEndpoint() {
        log.error("Error endpoint called — throwing intentional exception for demo");
        throw new RuntimeException("Intentional error — inspect this trace in Tempo!");
    }

    /**
     * Creates multiple orders in one call so you can quickly generate load.
     * Sends count=N POST-equivalent requests internally.
     */
    @PostMapping("/demo/bulk")
    public ResponseEntity<List<Order>> bulkCreate(@RequestParam(defaultValue = "5") int count) {
        log.info("Bulk creating {} orders", count);
        List<Order> created = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(orderService.createOrder("demo-customer-" + i, "product-" + i, 10.0 * (i + 1)));
        }
        return ResponseEntity.status(201).body(created);
    }

    // ─── Exception handlers ───────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleServerError(RuntimeException e) {
        log.error("Unhandled server error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }

    // ─── Request DTOs ─────────────────────────────────────────────────────────

    public record CreateOrderRequest(String customerId, String product, double amount) {}
}
