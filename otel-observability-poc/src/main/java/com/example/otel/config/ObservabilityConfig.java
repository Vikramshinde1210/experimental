package com.example.otel.config;

import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration.
 *
 * With the OTel Java Agent approach, there is nothing to configure here:
 * - HTTP server spans       → auto-created by the agent (Tomcat/Spring MVC)
 * - @WithSpan custom spans  → intercepted by the agent at bytecode level
 * - MDC injection           → done by the agent's Logback instrumentation
 * - OTLP export             → configured via OTEL_* env vars in docker-compose
 *
 * No ObservedAspect, no in-process SDK beans, no AspectJ needed.
 */
@Configuration
public class ObservabilityConfig {
    // intentionally empty
}
