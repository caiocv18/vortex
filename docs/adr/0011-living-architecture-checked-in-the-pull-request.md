# 11. Living architecture, checked in the pull request that changes it

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

The only architecture picture of the repository was a Mermaid `flowchart` in both READMEs, written by hand
and never checked. By 2026-11-20 it already missed the data pipeline, the user events of the authorization
service, OpenTelemetry and the hand-off between the two single-page apps. The longer TCC chapter in
`docs/tcc/` describes an earlier state on purpose and is not meant to follow the code.

Facts that shaped the decision, as of 2026-11-20:

- Nothing ties a change of Compose files, Terraform, broker settings or migrations to the docs, so the
  picture drifts silently.
- A required check whose workflow never starts stays pending forever, so a new required check cannot use
  `paths` in `on:` (ADR 0001); a skipped job reports success.
- Dependabot opens grouped pull requests every month that touch `pom.xml`, `package.json` and
  lockfiles, none of which move a box of the architecture.
- GitHub renders Mermaid in Markdown, but its `C4Context` syntax is experimental and renders poorly.
- A hand-drawn overview reads better in a README than a generated graph, and an SVG in an `<img>` cannot
  load web fonts, so the drawing must use a system font.

## Decision

- **Architecture as code in `docs/arquitetura/`**, in English with a `.pt-BR.md` mirror for every file:
  C4 level 1 (`context.md`) and level 2 (`containers.md`) as Mermaid `flowchart` with a C4 legend, and two
  runtime flows (`flows.md`) as `sequenceDiagram`: signing in and a stock movement through the broker.
- **One overview drawing:** `overview.excalidraw` is the source and `overview.svg` its export, both in
  the repository, in the same pull request. Both READMEs show the SVG and link to the folder, in place of
  the old flowchart. Text uses the Excalidraw "Normal" font (Helvetica).
- **The `architecture` check** (`.github/workflows/architecture.yml`), required in the ruleset: a
  `changes` job runs `dorny/paths-filter` with two filters, `key` and `docs`, and the `architecture` job
  fails when a key path changed and neither `docs/arquitetura/**` nor `docs/adr/**` did. It runs on every
  pull request and on `labeled` and `unlabeled`, always reports, and fails closed when the filter breaks.
- **Key paths** are the ones that move a box or an arrow: `infra/docker/**`, the Dockerfiles and nginx
  configs of the services, `infra/terraform/**/*.tf`, the broker, database and cloud folders under
  `infra/`, `application*.properties` of both backends, the Flyway migrations, `data/pipeline/src/**`,
  `data/schemas/*.json` and `start-vortex.sh`. `pom.xml`, `package.json` and the workflows are not.
- **Escape hatch:** the label `arquitetura-sem-mudanca` passes the check when a key path changed and the
  architecture did not (a port typo, a log level). The label is visible in the pull request history.

## Alternatives considered

- **A checkbox in a pull request template.** Costs nothing, but blocks nothing and is ticked by habit.
- **Generating the diagrams from the code** (Structurizr, a Compose-to-graph tool). Precise for
  containers, but it cannot draw intent (the hand-off, the fallback of the token validation) and adds a
  toolchain to CI.
- **The overview source in the author's notes, with only the SVG in the repository.** More comfortable to
  edit, but the source would leave the pull request and the next contributor could not change it.
- **A broad key path list, with `pom.xml` and `package.json`.** It would catch a stack change, but it
  would fail every Dependabot pull request or need a second exemption by author.
- **Mermaid `C4Context` / `C4Container`.** The right vocabulary, but experimental and badly laid out on
  GitHub today.

## Consequences

- A pull request that moves a box now carries its diagram change, reviewed in the same diff.
- The check costs a few seconds per pull request and reads the changed files from the API, with no
  checkout and no token beyond `pull-requests: read`.
- The `.pt-BR.md` mirrors double the writing of each diagram change; the check does not compare them.
- The overview SVG is exported by hand from Excalidraw. A wrong export is caught only by review.
- The label can be abused. It is visible in the pull request, which is enough for a single-owner
  repository; a team would want CODEOWNERS on `docs/arquitetura/` instead.
- Adding `architecture` to the required checks is a `PUT` of the whole ruleset (ADR 0005), done after this
  record's pull request is merged, so the check never blocks the pull request that creates it.

## References

- `docs/arquitetura/README.md`, the editing steps for the overview
- `.github/workflows/architecture.yml`
- ADR 0001, the paths filter and the rule against `paths` in `on:`
- ADR 0005, the ruleset
- C4 model: https://c4model.com
