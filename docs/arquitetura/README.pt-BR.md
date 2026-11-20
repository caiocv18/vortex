# Arquitetura

*[Read in English](README.md)*

A arquitetura do Vortex como código, mantida em dia pelos próprios pull requests que a mudam.

| Documento | O que mostra |
|---|---|
| [overview.svg](overview.svg) | Desenho do sistema inteiro numa página, usado nos READMEs. Fonte: [overview.excalidraw](overview.excalidraw) |
| [context.pt-BR.md](context.pt-BR.md) | C4 nível 1: o Vortex, quem o usa e os sistemas externos de que depende |
| [containers.pt-BR.md](containers.pt-BR.md) | C4 nível 2: as partes implantáveis, stacks, portas e ligações |
| [flows.pt-BR.md](flows.pt-BR.md) | Dois fluxos em execução: o login e um movimento de estoque pelo broker |

Os diagramas são blocos Mermaid `flowchart` e `sequenceDiagram` com legenda C4. A sintaxe `C4Context`
do próprio Mermaid ainda é experimental e renderiza mal no GitHub, por isso não é usada.

## Como se mantém em dia

O check `architecture` ([architecture.yml](../../.github/workflows/architecture.yml)) reprova o pull
request que muda um caminho-chave sem tocar em `docs/arquitetura/` nem em `docs/adr/`. Caminho-chave é o
que move uma caixa ou uma seta:

- arquivos do Compose, Dockerfiles e configurações do nginx
- Terraform (`infra/terraform/**/*.tf`) e as pastas de broker, banco e nuvem em `infra/`
- `application*.properties` dos dois backends (tópicos, filas, OpenTelemetry, URLs de serviço)
- migrations do Flyway, o código do pipeline de dados e os schemas do BigQuery
- `start-vortex.sh`

Troca de versão em `pom.xml` e `package.json` não é caminho-chave, então PR do Dependabot passa.

Quando um caminho-chave muda e a arquitetura não (porta digitada errado, nível de log), o rótulo
`arquitetura-sem-mudanca` no pull request faz o check rodar de novo e passar. Fora disso, atualize o
diagrama que a mudança toca, nos dois idiomas, e o SVG quando uma caixa ou seta da visão geral mudou.

## Como editar a visão geral

1. Abra `overview.excalidraw` em [excalidraw.com](https://excalidraw.com) (File, Open) ou no plugin
   Excalidraw do VS Code ou do Obsidian.
2. Edite e exporte como SVG, com "Embed scene" desligado e o fundo ligado, por cima de `overview.svg`.
3. Salve a cena por cima de `overview.excalidraw`. Os dois arquivos vão no mesmo pull request.
