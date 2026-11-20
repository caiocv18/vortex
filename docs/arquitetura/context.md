# Context (C4 level 1)

*[Leia em português](context.pt-BR.md)*

```mermaid
flowchart TB
    user([Inventory user<br/>operator, manager or admin])
    analyst([Analyst<br/>reads pseudonymized views])
    vortex[Vortex<br/>inventory control with<br/>sign-in, stock movements,<br/>alerts and reports]
    github[GitHub<br/>Actions, GHCR images,<br/>code scanning]
    aws[AWS<br/>CloudFront SPAs, on-demand<br/>demo instance, SQS, Route 53]
    gcp[Google Cloud<br/>BigQuery datasets of the<br/>data pipeline]
    mail[SMTP server<br/>password recovery e-mail]

    user -->|signs in, records movements,<br/>reads stock and reports| vortex
    vortex -->|sends reset links| mail
    vortex -->|events as NDJSON,<br/>loaded in batches| gcp
    analyst -->|queries views| gcp
    github -->|builds, scans and<br/>publishes the images| vortex
    aws -->|hosts the public demo| vortex

    classDef person fill:#08427b,color:#fff,stroke:#052e56
    classDef system fill:#1168bd,color:#fff,stroke:#0b4884
    classDef external fill:#999,color:#fff,stroke:#6b6b6b
    class user,analyst person
    class vortex system
    class github,aws,gcp,mail external
```

| Legend | |
|---|---|
| Dark blue, rounded | Person |
| Blue | The system described here |
| Grey | External system |

- The roles of an inventory user come from an explicit matrix with no hierarchy
  ([ADR 0002](../adr/0002-explicit-role-matrix-without-hierarchy.md)).
- The public demo runs on demand behind Session Manager
  ([ADR 0006](../adr/0006-on-demand-demo-instance-behind-session-manager.md)); the two single-page
  apps are static files on CloudFront ([ADR 0007](../adr/0007-static-spas-on-cloudfront.md)).
- Personal data reaches BigQuery only pseudonymized for analysts
  ([ADR 0008](../adr/0008-data-pipeline-on-google-cloud-with-pseudonymization.md)).
