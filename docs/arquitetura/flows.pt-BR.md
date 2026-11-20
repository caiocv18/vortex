# Fluxos em execução

*[Read in English](flows.md)*

## Login

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário do estoque
    participant S as App de login
    participant A as Serviço de autorização
    participant I as App de estoque
    participant P as Serviço de aplicação

    U->>S: e-mail e senha
    S->>A: POST /api/auth/login
    A-->>S: access e refresh token (JWT assinado com RSA)
    A-)A: evento de usuário, com um perfil de broker ligado
    S->>I: redireciona, refresh token no fragmento da URL
    I->>I: lê e limpa o fragmento antes do router
    I->>A: POST /api/auth/refresh
    A-->>I: access token novo
    I->>P: GET /api/... com Authorization: Bearer
    P->>A: POST /api/auth/validate-token (JwtAuthenticationFilter)
    alt o serviço de autorização responde
        A-->>P: valid, username, roles
    else a chamada falha (fallback do AuthServiceClient)
        P->>P: AuthTokenVerifier confere a assinatura com a chave pública
    end
    P-->>I: dados que a matriz de papéis permite
```

## Um movimento de estoque

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário do estoque
    participant I as App de estoque
    participant P as Serviço de aplicação
    participant D as Banco do estoque
    participant B as Broker (perfil kafka)

    U->>I: registra uma entrada ou saída
    I->>P: POST /api/movimentos
    P->>D: grava o movimento e atualiza o estoque
    P-->>I: 201 Created com o movimento
    opt broker disponível
        P-)B: vortex.movimento.estoque
        P-)B: vortex.auditoria
        P-)B: vortex.alertas.estoque, quando o estoque zera
        B-)P: listeners nos consumer groups vortex-*-group
        P->>P: EventosNegocioHandler reage a cada evento
    end
```

O broker é opcional: sem nenhum disponível, o `MovimentoEstoqueService` grava o movimento e pula os
eventos. Com RabbitMQ ou SQS, os mesmos eventos passam pela implementação daquele broker.
