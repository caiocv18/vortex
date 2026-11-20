# Architecture

*[Leia em português](README.pt-BR.md)*

The architecture of Vortex as code, kept current by the pull requests that change it.

| Document | What it shows |
|---|---|
| [overview.svg](overview.svg) | One-page drawing of the whole system, for the READMEs. Source: [overview.excalidraw](overview.excalidraw) |
| [context.md](context.md) | C4 level 1: Vortex, the people who use it and the external systems it depends on |
| [containers.md](containers.md) | C4 level 2: the deployable parts, their stacks, ports and connections |
| [flows.md](flows.md) | Two runtime flows: signing in, and a stock movement through the broker |

The diagrams are Mermaid `flowchart` and `sequenceDiagram` blocks with a C4 legend. Mermaid's own
`C4Context` syntax is still experimental and renders poorly on GitHub, so it is not used.

## Keeping it current

The `architecture` check ([architecture.yml](../../.github/workflows/architecture.yml)) fails a pull
request that changes a key path without touching `docs/arquitetura/` or `docs/adr/`. Key paths are the
ones that move a box or an arrow:

- Compose files, Dockerfiles and nginx configs
- Terraform (`infra/terraform/**/*.tf`) and the broker, database and cloud folders under `infra/`
- `application*.properties` of both backends (topics, queues, OpenTelemetry, service URLs)
- Flyway migrations, the data pipeline sources and the BigQuery schemas
- `start-vortex.sh`

Version bumps of `pom.xml` and `package.json` are not key paths, so Dependabot pull requests pass.

When a key path changes but the architecture does not (a port typo, a log level), add the label
`arquitetura-sem-mudanca` to the pull request: the check re-runs and passes. Otherwise update the
diagram the change touches, in both languages, and the SVG when a box or arrow of the overview moved.

## Editing the overview

1. Open `overview.excalidraw` at [excalidraw.com](https://excalidraw.com) (File, Open) or in the
   Excalidraw plugin of VS Code or Obsidian.
2. Edit, then export as SVG with "Embed scene" off and the background on, over `overview.svg`.
3. Save the scene back over `overview.excalidraw`. Both files go in the same pull request.
