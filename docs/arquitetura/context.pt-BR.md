# Contexto (C4 nível 1)

*[Read in English](context.md)*

```mermaid
flowchart TB
    user([Usuário do estoque<br/>operador, gerente ou admin])
    analyst([Analista<br/>lê visões pseudonimizadas])
    vortex[Vortex<br/>controle de estoque com<br/>login, movimentos,<br/>alertas e relatórios]
    github[GitHub<br/>Actions, imagens no GHCR,<br/>code scanning]
    aws[AWS<br/>SPAs no CloudFront, instância<br/>de demo sob demanda, SQS, Route 53]
    gcp[Google Cloud<br/>datasets do BigQuery do<br/>pipeline de dados]
    mail[Servidor SMTP<br/>e-mail de recuperação de senha]

    user -->|faz login, registra movimentos,<br/>consulta estoque e relatórios| vortex
    vortex -->|envia links de redefinição| mail
    vortex -->|eventos em NDJSON,<br/>carregados em lote| gcp
    analyst -->|consulta as visões| gcp
    github -->|compila, varre e<br/>publica as imagens| vortex
    aws -->|hospeda a demo pública| vortex

    classDef person fill:#08427b,color:#fff,stroke:#052e56
    classDef system fill:#1168bd,color:#fff,stroke:#0b4884
    classDef external fill:#999,color:#fff,stroke:#6b6b6b
    class user,analyst person
    class vortex system
    class github,aws,gcp,mail external
```

| Legenda | |
|---|---|
| Azul-escuro, arredondado | Pessoa |
| Azul | O sistema descrito aqui |
| Cinza | Sistema externo |

- Os papéis do usuário do estoque vêm de uma matriz explícita, sem hierarquia
  ([ADR 0002](../adr/0002-explicit-role-matrix-without-hierarchy.md)).
- A demo pública sobe sob demanda, atrás do Session Manager
  ([ADR 0006](../adr/0006-on-demand-demo-instance-behind-session-manager.md)); as duas SPAs são arquivos
  estáticos no CloudFront ([ADR 0007](../adr/0007-static-spas-on-cloudfront.md)).
- Dado pessoal só chega ao analista pseudonimizado
  ([ADR 0008](../adr/0008-data-pipeline-on-google-cloud-with-pseudonymization.md)).
