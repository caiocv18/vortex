# 5. A master ruleset without bypass, Dependabot with a cooldown and immutable releases

- **Status:** Accepted
- **Date:** 2026-11-14
- **Pull request:** #15

## Context

Until now `master` had no protection. Anyone with write access, and any workflow token with `contents: write` such as the coverage badge job's, could push to it directly, rewrite it or delete it. The repository has one maintainer, so a rule that waits for a second person's review would block every change.

Nothing kept the dependencies current across the six places that declare them: the Maven poms, the npm manifests, the SHA pins of the workflows, the base images of the Dockerfiles, the images of the Compose files and the Terraform provider pins with their lock files. The first Trivy scan of the application image (ADR 0004) already reported seven HIGH findings that have a fix.

There was no release, and the four modules disagreed on their own version: `0.0.1-SNAPSHOT`, `1.0.0-SNAPSHOT`, `0.0.0` and `1.0.0`.

## Decision

- **Ruleset `master-protection`**, active on the default branch, with no bypass actor, the owner included:
  - `deletion` and `non_fast_forward`: `master` cannot be deleted or force-pushed.
  - `pull_request` with zero required approvals: every change reaches `master` through a pull request, without waiting for a reviewer who does not exist.
  - `required_status_checks`: the five build jobs (`app-service`, `auth-service`, `front-app`, `front-auth`, `launcher`) from the GitHub Actions app and `SonarCloud Code Analysis` from the SonarCloud app. Each check is bound to its app's integration id, so no other app can satisfy it by reporting the same name. The branch need not be up to date with `master` before merging.
  - `required_signatures`: every new commit that reaches `master` must carry a verified signature. The rule checks what a push or a merge introduces, so one 2025 commit, `a6382c9`, stays unverified in the history. GitHub signs the merge and squash commits it creates, but a rebase merge recreates the commits without signature verification, so the ruleset allows only `merge` and `squash`, and the repository setting turns rebase merging off.
- **CodeQL default setup** for `actions`, `java-kotlin` and `javascript-typescript`, on pushes, on pull requests and weekly. It is not a required check, and the ruleset has no `code_scanning` rule yet.
- **Dependabot:**
  - alerts and automated security fixes are on, for the dependencies that GitHub's dependency graph sees;
  - version updates run monthly for `maven`, `npm`, `github-actions`, `docker`, `docker-compose` and `terraform`, with minor and patch updates grouped per ecosystem and each major update in its own pull request;
  - a seven-day cooldown keeps every new release out of the version updates for a week, so a hijacked release has time to be caught first. Security updates skip both the schedule and the cooldown.
- **Releases:** immutable releases are on. A release is an annotated, signed tag `vX.Y.Z` on a commit of `master`, whose push makes `images.yml` publish `X.Y.Z`, `X.Y` and `latest` (ADR 0004). The release itself carries notes written by hand, followed by GitHub's generated list of pull requests.
- **Version 0.1.0** everywhere: both poms, both `package.json` files and their lockfiles, the OpenAPI `info.version` of both services, the OpenAPI snapshot and the generated TypeScript client.

## Alternatives considered

- **Classic branch protection.** It cannot restrict the merge methods per branch, and its full settings are readable only by admins. Anyone can read a ruleset through `GET /repos/{owner}/{repo}/rules/branches/master`, which suits a public repository that shows how it is run.
- **A bypass for the owner.** It would turn every rule into a suggestion for the only person who merges. Turning the ruleset off remains possible in an emergency, as a deliberate change to the ruleset instead of a silent push.
- **One required approval.** GitHub does not let an author approve their own pull request, so with one maintainer nothing could ever merge.
- **Strict status checks.** They would force every open branch to merge `master` again after each merge. With one maintainer and per-service builds, that costs more reruns than it prevents broken combinations, and the `master` runs still catch those after the merge.
- **Requiring CodeQL now, or a `code_scanning` rule.** Its first analyses opened six alerts that still need triage, and CodeQL 2.27.4 lists Java only up to 26, so the Java results are provisional. Blocking merges on them comes later, once the alerts are triaged.
- **Weekly version updates.** More pull requests to read for a single maintainer, while the security updates that matter do not wait for the schedule anyway.
- **Renovate.** More flexible, but it needs a third-party app with write access to the repository, where Dependabot is built into GitHub.

## Consequences

- Nobody can push to `master`, the owner included. The coverage badge job keeps working because it pushes only to the `badges` branch, which the ruleset does not cover.
- A required check that never reports blocks the merge forever. That is why the per-service workflows have no `paths` in `on:` (ADR 0001) and why `images.yml` and CodeQL must never become required. SonarCloud has to report on every pull request, Dependabot's included; if it stops, the ruleset has to change before anything merges.
- CodeQL's Java analysis uses `build-mode: none` and extracted the Java 27 sources without errors on 2026-11-14, even though Java 27 is not on CodeQL's supported list. The result stays provisional until it is.
- Publishing a release locks its tag and its assets: the tag cannot move, and its name cannot be reused even after the release is deleted. A release on the wrong commit is superseded by a new version. The title, the notes and the pre-release and latest flags stay editable, so a mistake in the notes is fixed in place.
- GitHub's dependency graph reads the poms statically. It sees the Maven dependencies declared there, but not the jars that the Spring Boot parent and the Quarkus BOM bring in. The seven HIGH findings of the first Trivy scan live in those jars (`amqp-client`, `snappy-java` and `lz4-java`) and raise no Dependabot alert: they move when a monthly update bumps the parent or the BOM, and Trivy's code scanning results are what flags them. Submitting the resolved Maven tree to the dependency graph would close that gap, in a separate change that keeps Maven out of any job holding a write token.
- `master` now carries the released version `0.1.0`, not a snapshot. Every later release bumps it in the pull request that prepares that release.

## References

- `GET /repos/caiocv18/vortex/rulesets` and `GET /repos/caiocv18/vortex/rules/branches/master`: the ruleset as GitHub applies it.
- `.github/dependabot.yml`: the six ecosystems, the groups and the cooldown.
- `CLAUDE.md`, section "Governance": the release steps and the rules for changing the ruleset.
- ADR 0001 (why required checks always report) and ADR 0004 (the images that a release publishes).
