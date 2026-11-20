# Containers (C4 level 2)

*[Leia em português](containers.pt-BR.md)*

```mermaid
flowchart LR
    user([Inventory user])
    subgraph vortex [Vortex]
        signin[Sign-in app<br/>React 19, nginx<br/>:3001]
        inventory[Inventory app<br/>Vue 3 + Vuetify, nginx<br/>:5173]
        app[Application service<br/>Spring Boot 4.1<br/>:8080]
        auth[Authorization service<br/>Quarkus 3.40<br/>:8081]
        appdb[(Inventory database<br/>H2, PostgreSQL or<br/>Oracle Free by profile)]
        authdb[(Accounts database<br/>PostgreSQL 18)]
        broker{{Message broker<br/>Kafka, RabbitMQ or SQS<br/>optional, one per profile}}
        pipeline[Data pipeline<br/>Apache Beam, batch]
    end
    otel[otel-lgtm<br/>OTLP :4318, Grafana<br/>optional]
    bq[(BigQuery<br/>raw, curated, analytics)]
    mail[SMTP server]

    user --> signin
    user --> inventory
    signin -->|sign-up, sign-in, recovery<br/>REST| auth
    signin -.->|redirect with a refresh<br/>token in the fragment| inventory
    inventory -->|refresh, logout<br/>REST| auth
    inventory -->|/api, Bearer JWT<br/>REST| app
    app -->|validate-token per request;<br/>public key as fallback| auth
    app -->|JPA, Flyway| appdb
    auth -->|Panache, Flyway| authdb
    app -->|movements, products, alerts,<br/>reports, audit| broker
    broker -->|listeners of the same service| app
    auth -->|user events<br/>kafka or rabbitmq profile| broker
    auth --> mail
    app -.->|OTLP| otel
    auth -.->|OTLP| otel
    pipeline -->|NDJSON per table| bq

    classDef person fill:#08427b,color:#fff,stroke:#052e56
    classDef container fill:#438dd5,color:#fff,stroke:#2e6295
    classDef store fill:#438dd5,color:#fff,stroke:#2e6295
    classDef external fill:#999,color:#fff,stroke:#6b6b6b
    class user person
    class signin,inventory,app,auth,broker,pipeline container
    class appdb,authdb store
    class otel,bq,mail external
```

| Legend | |
|---|---|
| Solid arrow | Synchronous call or write |
| Dotted arrow | Redirect or optional telemetry |
| Hexagon | Broker: only one is active, picked by profile |
| Cylinder | Database |
| Grey | Outside the repository's runtime |

| Container | Path | Notes |
|---|---|---|
| Sign-in app | `frontend/vortex-authorization-service` | Hands the session to the inventory app through the URL fragment |
| Inventory app | `frontend/vortex-application-service` | Client generated from `docs/application/openapi-backend.json`; nginx proxies `/api/` to the application service |
| Application service | `backend/vortex-application-service` | `MessageBrokerService` has one implementation per broker; the database comes from the profile table in the [README](../../README.md#architecture) |
| Authorization service | `backend/vortex-authorization-service` | Issues RSA-signed JWTs; publishes `vortex-user-events` (Kafka) or `vortex.user.events` (RabbitMQ) |
| Data pipeline | `data/pipeline` | Reads NDJSON exports of the events, pseudonymizes, writes one file per table; `bigquery_sandbox` loads them |
| otel-lgtm | `infra/docker/docker-compose.observabilidade.yml` | Traces, metrics and logs with no agent ([ADR 0009](../adr/0009-native-opentelemetry-without-an-agent.md)) |

Kafka topics of the application service (`application-kafka.properties`): `vortex.movimento.estoque`,
`vortex.produto.events`, `vortex.alertas.estoque`, `vortex.relatorios.events` and `vortex.auditoria`.
