# Runtime flows

*[Leia em português](flows.pt-BR.md)*

## Signing in

```mermaid
sequenceDiagram
    autonumber
    actor U as Inventory user
    participant S as Sign-in app
    participant A as Authorization service
    participant I as Inventory app
    participant P as Application service

    U->>S: e-mail and password
    S->>A: POST /api/auth/login
    A-->>S: access and refresh tokens (RSA-signed JWT)
    A-)A: user event, when a broker profile is on
    S->>I: redirect, refresh token in the URL fragment
    I->>I: reads and clears the fragment before the router
    I->>A: POST /api/auth/refresh
    A-->>I: new access token
    I->>P: GET /api/... with Authorization: Bearer
    P->>A: POST /api/auth/validate-token (JwtAuthenticationFilter)
    alt authorization service answers
        A-->>P: valid, username, roles
    else call fails (fallback of AuthServiceClient)
        P->>P: AuthTokenVerifier checks the signature with the public key
    end
    P-->>I: data allowed by the role matrix
```

## A stock movement

```mermaid
sequenceDiagram
    autonumber
    actor U as Inventory user
    participant I as Inventory app
    participant P as Application service
    participant D as Inventory database
    participant B as Broker (Kafka profile)

    U->>I: records an inbound or outbound movement
    I->>P: POST /api/movimentos
    P->>D: saves the movement, updates the stock
    P-->>I: 201 Created with the movement
    opt broker available
        P-)B: vortex.movimento.estoque
        P-)B: vortex.auditoria
        P-)B: vortex.alertas.estoque, when the stock runs out
        B-)P: listeners in the vortex-*-group consumer groups
        P->>P: EventosNegocioHandler reacts to each event
    end
```

The broker is optional: with none available, `MovimentoEstoqueService` saves the movement and skips the
events. With RabbitMQ or SQS the same events go through the implementation of that broker.
