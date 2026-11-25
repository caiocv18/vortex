# Containers (C4 nível 2)

*[Read in English](containers.md)*

```mermaid
flowchart LR
    user([Usuário do estoque])
    subgraph vortex [Vortex]
        signin[App de login<br/>React 19, nginx<br/>:3001]
        inventory[App de estoque<br/>Vue 3 + Vuetify, nginx<br/>:5173]
        app[Serviço de aplicação<br/>Spring Boot 4.1<br/>:8080]
        auth[Serviço de autorização<br/>Quarkus 3.40<br/>:8081]
        appdb[(Banco do estoque<br/>H2, PostgreSQL ou<br/>Oracle Free por perfil)]
        authdb[(Banco de contas<br/>PostgreSQL 18)]
        broker{{Broker de mensagens<br/>Kafka, RabbitMQ ou SQS<br/>opcional, um por perfil}}
        pipeline[Pipeline de dados<br/>Apache Beam, em lote]
    end
    otel[otel-lgtm<br/>OTLP :4318, Grafana<br/>opcional]
    bq[(BigQuery<br/>raw, curated, analytics)]
    mail[Servidor SMTP]

    user --> signin
    user --> inventory
    signin -->|cadastro, login, recuperação<br/>REST| auth
    signin -.->|redireciona com o refresh<br/>token no fragmento| inventory
    inventory -->|refresh, logout<br/>REST| auth
    inventory -->|/api, Bearer JWT<br/>REST| app
    app -->|validate-token a cada requisição;<br/>chave pública no fallback| auth
    app -->|JPA, Flyway| appdb
    auth -->|Panache, Flyway| authdb
    app -->|movimentos, produtos, alertas,<br/>relatórios, auditoria| broker
    broker -->|listeners do mesmo serviço| app
    auth -->|eventos de usuário<br/>perfil kafka ou rabbitmq| broker
    auth --> mail
    app -.->|OTLP| otel
    auth -.->|OTLP| otel
    pipeline -->|carga NDJSON ou<br/>carga em lote do Dataflow| bq

    classDef person fill:#08427b,color:#fff,stroke:#052e56
    classDef container fill:#438dd5,color:#fff,stroke:#2e6295
    classDef store fill:#438dd5,color:#fff,stroke:#2e6295
    classDef external fill:#999,color:#fff,stroke:#6b6b6b
    class user person
    class signin,inventory,app,auth,broker,pipeline container
    class appdb,authdb store
    class otel,bq,mail external
```

| Legenda | |
|---|---|
| Seta cheia | Chamada síncrona ou escrita |
| Seta pontilhada | Redirecionamento ou telemetria opcional |
| Hexágono | Broker: só um fica ativo, escolhido pelo perfil |
| Cilindro | Banco de dados |
| Cinza | Fora do runtime do repositório |

| Container | Caminho | Observações |
|---|---|---|
| App de login | `frontend/vortex-authorization-service` | Passa a sessão ao app de estoque pelo fragmento da URL |
| App de estoque | `frontend/vortex-application-service` | Cliente gerado de `docs/application/openapi-backend.json`; o nginx repassa `/api/` ao serviço de aplicação |
| Serviço de aplicação | `backend/vortex-application-service` | `MessageBrokerService` tem uma implementação por broker; o banco sai da tabela de perfis do [README](../../README.pt-BR.md#arquitetura) |
| Serviço de autorização | `backend/vortex-authorization-service` | Emite JWT assinado com RSA; publica `vortex-user-events` (Kafka) ou `vortex.user.events` (RabbitMQ) |
| Pipeline de dados | `data/pipeline` | Lê exportações NDJSON dos eventos, pseudonimiza e grava um arquivo por tabela (execução local; o `bigquery_sandbox` carrega) ou, como job do Dataflow, acrescenta no BigQuery |
| otel-lgtm | `infra/docker/docker-compose.observabilidade.yml` | Traces, métricas e logs sem agente ([ADR 0009](../adr/0009-native-opentelemetry-without-an-agent.md)) |

Tópicos Kafka do serviço de aplicação (`application-kafka.properties`): `vortex.movimento.estoque`,
`vortex.produto.events`, `vortex.alertas.estoque`, `vortex.relatorios.events` e `vortex.auditoria`.
