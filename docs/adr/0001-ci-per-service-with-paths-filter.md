# 1. One CI workflow per service, gated by a paths filter

- **Status:** Accepted
- **Date:** 2026-11-13 (first applied on 2026-11-09)
- **Pull requests:** #3 (`app-service`), #8 (the other four services)

## Context

The repository is a monorepo with four services and a launcher script:

- `backend/vortex-application-service` (Spring Boot, Testcontainers, a JaCoCo gate);
- `backend/vortex-authorization-service` (Quarkus, a JaCoCo gate);
- `frontend/vortex-application-service` (Vue) and `frontend/vortex-authorization-service` (React);
- `start-vortex.sh` and `scripts/`.

A full build of everything takes far longer than any single service, and most pull requests touch one service. The checks must still be usable as required status checks in a branch ruleset, and a required check that never reports blocks the merge forever.

## Decision

- One workflow file per service in `.github/workflows/`: `app-service.yml`, `auth-service.yml`, `front-app.yml`, `front-auth.yml` and `launcher.yml`.
- Every workflow runs on every `pull_request` and on `push` to `master`, with no `paths` key in `on:`.
- A first job, `changes`, runs `dorny/paths-filter` against the service folder and the workflow file itself. The build job, named after the workflow, runs only when that filter matched.
- The build job fails closed: its `if:` also runs it when `changes` did not succeed, because a skipped job reports success.

## Alternatives considered

- **One workflow that builds everything.** Simple, but every pull request pays for the slowest service, and a failure in one service blocks unrelated work.
- **`paths` in `on:`.** GitHub never starts a workflow whose paths do not match, so its check never reports. As a required check it stays pending and the pull request cannot merge.
- **One workflow with a matrix over the services.** It yields a single check name, so a ruleset cannot require each service separately, and per-entry path conditions get awkward.

## Consequences

- A pull request runs only the builds it can affect; the other build jobs report `skipped`, which counts as success.
- There are five checks named `changes`, one per workflow. A ruleset must require only the build jobs (`app-service`, `auth-service`, `front-app`, `front-auth`, `launcher`).
- Editing a workflow file triggers its own build, so CI changes are tested by the change itself.
- On `push` to `master` the filter diffs against the previous commit, so the `changes` job checks out the repository there; on `pull_request` it uses the API.
- A new service needs its own workflow with the same two-job shape.

## References

- `.github/workflows/app-service.yml:15-43`: the `changes` job, the filter and the fail-closed `if:`.
- `.github/workflows/launcher.yml`: the same shape for the shell scripts.
- `CLAUDE.md`, section "CI (GitHub Actions)": the workflow table, check names, triggers and conventions.
