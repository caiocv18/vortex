# 9. Native OpenTelemetry in both backends, without an agent, exported only on demand

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

Neither backend had telemetry. The application service had no Actuator, so the `management.*` lines of the `kafka` and `rabbitmq` profiles were dead, and the authorization service had `quarkus-micrometer` with no registry. A request that reads a report crosses both services and runs dozens of SQL statements, and none of that was visible anywhere: the report `GET /api/relatorios/lucro-por-produto` is known to run two queries per product, and there was no way to show it.

Facts that shaped the design, as of 2026-11-19:

- Spring Boot 4.1 ships `spring-boot-starter-opentelemetry`: Micrometer Tracing over the OpenTelemetry SDK, a Micrometer OTLP registry for metrics and an OTLP log exporter. It does not install the Logback appender, so no log reaches OTLP without one more piece.
- Quarkus 3.40 ships `quarkus-opentelemetry` and `quarkus-micrometer-opentelemetry`. Its exporters can only be switched off at build time; at run time the only switch is `quarkus.otel.sdk.disabled`, which turns the whole SDK off.
- The OpenTelemetry Java agent has no confirmed support for Java 27, which both services run.
- The application service calls the authorization service inside `CompletableFuture.supplyAsync`, on the common fork-join pool, which knows nothing about the request's observation.
- `grafana/otel-lgtm` bundles an OpenTelemetry Collector, Tempo, Loki, Prometheus and Grafana in one container, with the Loki-to-Tempo link on `trace_id` already configured. Measured on 2026-11-19: 416 MB idle, 1.35 GB after a few report traces.
- The local security chain of the application service ends in `anyRequest().permitAll()`, so any Actuator endpoint exposed there is open without a token.

## Decision

- **Application service:** `spring-boot-starter-actuator`, `spring-boot-starter-opentelemetry`, `datasource-micrometer-spring-boot` 2.3.1 for a span per JDBC statement and result set, and `opentelemetry-logback-appender-1.0` 2.28.0-alpha, the release built against the OpenTelemetry SDK 1.62.0 that Boot 4.1.2 manages. `OpenTelemetryLogbackConfig` adds the appender to the root logger in code, so Boot's own logging setup, including structured logging, stays in charge.
- **Context across threads:** the `AuthServiceClient` runs its `supplyAsync` calls on a virtual-thread executor wrapped by `ContextExecutorService` from Micrometer context propagation. The client span of `POST /api/auth/validate-token` becomes a child of the request span, and the `traceparent` header carries the trace into the authorization service. The base URL moved into the `RestClient` builder, so span names and the `uri` tag carry the path only. This covers the first attempt, which is the normal path; a retry or a timeout fallback runs on Resilience4j's scheduler, which carries no context propagator, so those attempts still start a trace of their own, as all of them did before.
- **Authorization service:** `quarkus-opentelemetry` with logs and metrics on, `quarkus-micrometer-opentelemetry` in place of `quarkus-micrometer`, `quarkus.datasource.jdbc.telemetry=true` and `quarkus-logging-json`.
- **One switch, off by default:** `VORTEX_OTEL_ENABLED=true` turns the OTLP export on in both services. Spring maps it to `management.tracing.export.otlp.enabled`, `management.logging.export.otlp.enabled` and `management.otlp.metrics.export.enabled`, so spans and trace ids exist even with the export off. Quarkus maps it to `quarkus.otel.sdk.disabled` through a nested expression (`${vortex.otel.sdk-disabled-when-enabled.${VORTEX_OTEL_ENABLED:false}}`), because SmallRye Config cannot negate a boolean; a value other than `true` or `false` stops the boot. Both export OTLP over HTTP to `VORTEX_OTEL_ENDPOINT`, default `http://localhost:4318`.
- **Sampling:** the application service samples every trace in `dev` and `leve` and keeps Boot's 10% default elsewhere. The authorization service keeps Quarkus' `parentbased_always_on`, so it follows the caller's decision.
- **Logs:** JSON on the console (ECS) only where a log collector would read stdout: the application service's `cloud` profile and the authorization service's `prod` profile. Local profiles keep the text pattern; the application service's pattern already shows the trace id, while the authorization service's default Quarkus pattern prints no MDC. Logs reach Loki through OTLP in every profile when the export is on.
- **Actuator:** only `health` is exposed, without details and without the discovery page, in every profile, including `kafka` and `rabbitmq`. The `cloud` profile still denies `/actuator/**` entirely; its probe stays `/health`.
- **Local backend:** `infra/docker/docker-compose.observabilidade.yml` runs `grafana/otel-lgtm:0.41.0` with a 2 GB memory limit, ports bound to the loopback only (Grafana on 3030, OTLP on 4317 and 4318). Grafana keeps the image's anonymous admin, which Explore needs, and enforces the `localhost` domain, so a page that rebinds its own hostname to the loopback gets a redirect instead of the API. The export reaches it from the backends that run on the host (environment `dev`, modes `leve` and `dev`); with `-e prd` the application service runs in a container without the variables. The container is named `otel-lgtm`, without "vortex", because the launcher stops every container whose name contains "vortex" before it starts.

## Alternatives considered

- **The OpenTelemetry Java agent:** broader auto-instrumentation with no code, but no confirmed Java 27 support, and a second instrumentation layer beside Micrometer Observation, which Spring MVC, Spring Security and the `RestClient` already feed.
- **Export always on in the local profiles:** without a collector each exporter logs a connection error every few seconds, and CI has no collector. Off by default keeps the local logs and the builds clean.
- **A `logback-spring.xml` with the appender:** it replaces Boot's programmatic logging setup, so `logging.structured.format.console` would need its own XML per profile.
- **Propagating with `spring.task.execution.propagate-context` on Boot's task executor:** any `Executor` bean of our own makes Boot back off from its `applicationTaskExecutor`, and reusing the shared executor ties the auth calls to every other async task. A private executor for the client keeps the blast radius in one class.
- **A profile in the main compose files:** couples the observability stack to the launcher's lifecycle and to composes with their own password rules.
- **Grafana Cloud now:** planned as a later step, with the same OTLP export pointed at its gateway through `VORTEX_OTEL_ENDPOINT`.

## Consequences

- One trace of the profit report shows the HTTP span, the call to the authorization service with its two SQL statements, and 51 statements in the application service for 20 products and 10 product types: one `findAll`, two sums per product, and one lazy load per product type. That trace is the baseline the report's fix will be measured against.
- A log line in Loki opens its trace in Tempo through `trace_id`.
- The application's text logs show the trace id in every profile, also with the export off. The authorization service has trace ids only while the export is on.
- `VORTEX_OTEL_ENABLED` accepts only `true` or `false` in the authorization service.
- The observability stack costs about 1.4 GB beside the 0.65 GB of the light mode, so it must not run beside Airflow on the 6 GB WSL VM.
- Unexposed paths under `/actuator/` answer 500, not 404, because `GlobalExceptionHandler` turns `NoResourceFoundException` into a generic error. That predates this change and is left for its own fix.

## References

- Spring Boot 4.1 reference, *Tracing* and *Observability*: https://docs.spring.io/spring-boot/reference/actuator/tracing.html
- Quarkus, *Using OpenTelemetry*: https://quarkus.io/guides/opentelemetry
- datasource-micrometer: https://github.com/jdbc-observations/datasource-micrometer
- Micrometer context propagation: https://docs.micrometer.io/context-propagation/reference/usage.html
- grafana/docker-otel-lgtm: https://github.com/grafana/docker-otel-lgtm
