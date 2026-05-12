package com.example.otel.service;

import com.example.otel.metrics.BusinessMetrics;
import com.example.otel.model.Order;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order service demonstrating all three observability pillars.
 *
 * TRACING  — @WithSpan creates a child OTel span for each method.
 *            The OTel Java agent intercepts this via bytecode instrumentation
 *            (no AOP needed). The agent also auto-creates a parent span for
 *            every incoming HTTP request (Spring MVC / Tomcat instrumentation).
 *            Spans flow: HTTP span → createOrder span → (timer record span).
 *
 * LOGS     — The OTel agent automatically injects trace_id and span_id into
 *            Logback MDC for the lifetime of each active span. Every log line
 *            emitted inside an active span carries these IDs, linking logs to
 *            traces without any manual code.
 *
 * METRICS  — BusinessMetrics uses Micrometer counters / timers which are
 *            independent of the OTel agent. Prometheus scrapes them from
 *            /actuator/prometheus.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Map<String, Order> store   = new ConcurrentHashMap<>();
    private final BusinessMetrics    metrics;

    public OrderService(BusinessMetrics metrics) {
        this.metrics = metrics;
    }

    @WithSpan("order.create")
    public Order createOrder(
            @SpanAttribute("customer.id") String customerId,
            @SpanAttribute("order.product") String product,
            double amount) {

        log.info("Creating order customerId={} product={} amount={}", customerId, product, amount);

        return metrics.orderProcessingTimer().record(() -> {
            if (amount <= 0) {
                log.warn("Order rejected — invalid amount={}", amount);
                metrics.recordOrderFailed();
                throw new IllegalArgumentException("Amount must be positive, got: " + amount);
            }

            Order order = Order.create(customerId, product, amount);
            store.put(order.id(), order);
            metrics.recordOrderCreated();
            metrics.incrementActive();

            log.info("Order created orderId={} status={}", order.id(), order.status());
            return order;
        });
    }

    @WithSpan("order.find")
    public Optional<Order> findOrder(@SpanAttribute("order.id") String id) {
        log.debug("Looking up order orderId={}", id);
        return Optional.ofNullable(store.get(id));
    }

    public List<Order> listOrders() {
        return new ArrayList<>(store.values());
    }

    @WithSpan("order.cancel")
    public Order cancelOrder(@SpanAttribute("order.id") String id) {
        Order order = store.get(id);
        if (order == null) {
            log.warn("Cancel requested for unknown orderId={}", id);
            throw new NoSuchElementException("Order not found: " + id);
        }
        Order cancelled = order.withStatus("CANCELLED");
        store.put(id, cancelled);
        metrics.decrementActive();
        log.info("Order cancelled orderId={}", id);
        return cancelled;
    }
}
