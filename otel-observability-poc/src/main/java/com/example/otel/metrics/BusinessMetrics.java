package com.example.otel.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Micrometer metrics for business-level observability.
 *
 * Metric naming convention:  dots in code → underscores in Prometheus
 *   orders.created  → orders_created_total   (Counter — auto-suffixed)
 *   orders.failed   → orders_failed_total
 *   orders.active   → orders_active           (Gauge)
 *   orders.processing.duration → orders_processing_duration_seconds (Timer)
 *
 * All metrics carry the application tag added globally in application.yml
 * (management.metrics.tags.application) so you can filter in Grafana.
 */
@Component
public class BusinessMetrics {

    private final Counter           orderCreatedCounter;
    private final Counter           orderFailedCounter;
    private final AtomicInteger     activeOrderCount;
    private final Timer             orderProcessingTimer;

    public BusinessMetrics(MeterRegistry registry) {

        orderCreatedCounter = Counter.builder("orders.created")
                .description("Total number of orders created successfully")
                .tag("service", "order-service")
                .register(registry);

        orderFailedCounter = Counter.builder("orders.failed")
                .description("Total number of orders that failed validation or processing")
                .tag("service", "order-service")
                .register(registry);

        // Gauge wraps an AtomicInteger — Micrometer samples it on each scrape.
        AtomicInteger active = new AtomicInteger(0);
        activeOrderCount = active;
        Gauge.builder("orders.active", active, AtomicInteger::get)
                .description("Current number of active (non-cancelled) orders held in memory")
                .tag("service", "order-service")
                .register(registry);

        // Timer records both count and duration, and publishes percentile histograms
        // so Grafana can compute p50/p95/p99 via histogram_quantile().
        orderProcessingTimer = Timer.builder("orders.processing.duration")
                .description("End-to-end time to validate and persist an order")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordOrderCreated()  { orderCreatedCounter.increment(); }
    public void recordOrderFailed()   { orderFailedCounter.increment();  }
    public void incrementActive()     { activeOrderCount.incrementAndGet(); }
    public void decrementActive()     { activeOrderCount.decrementAndGet(); }
    public Timer orderProcessingTimer() { return orderProcessingTimer; }
}
