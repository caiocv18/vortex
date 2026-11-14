# 4. Multi-arch images on GHCR, built without emulation and scanned by Trivy

- **Status:** Accepted
- **Date:** 2026-11-14
- **Pull request:** #14

## Context

The cloud demo will run on an arm64 (Graviton) instance, while contributors and CI runners are mostly amd64. Until now the backends had Dockerfiles but no published image, so every deployment would have to build from source on the target machine.

Publishing needs a token that can write packages. After the badge job's `contents: write`, that is the most sensitive token in the CI, and the build that produces the image runs third-party code: Maven plugins and every dependency they resolve.

The authorization service's Dockerfile copies a `target/quarkus-app` built beforehand, so its Maven build cannot happen inside the image build. The application service's Dockerfile builds the jar itself, in a JDK stage.

## Decision

- **Registry:** the GitHub Container Registry, as `ghcr.io/caiocv18/vortex-application-service` and `ghcr.io/caiocv18/vortex-authorization-service`, for `linux/amd64` and `linux/arm64`. Both packages are to be public: GHCR creates them private, so each one is switched by hand after the first publish (see Consequences).
- **Workflow:** `.github/workflows/images.yml`, with `permissions: {}` at the top and one job per concern:

  | Job | Runs on | Permissions | What it does |
  |---|---|---|---|
  | `package-auth` | every event | `contents: read` | `./mvnw -B -ntp package -DskipTests` for the authorization service, uploaded as an artifact |
  | `build` | pull requests that touch `backend/**` or the workflow | `contents: read` | builds both images for both platforms, without pushing |
  | `publish` | `master` (push, weekly schedule, manual run) and `vX.Y.Z` tags | `contents: read`, `packages: write` | builds and pushes both images, with labels and index annotations |
  | `smoke-arm64` | after `publish` | `packages: read` | runs the application image on an `ubuntu-24.04-arm` runner with `dev,leve` (H2) and waits for `/health` |
  | `scan` | after `publish` | `packages: read` | Trivy on the arm64 image, CRITICAL and HIGH with a fix available, as SARIF |
  | `upload-sarif` | after `scan` | `security-events: write` | sends each SARIF to the Security tab, as `trivy-app` and `trivy-auth` |

- **One build per image, no emulation:** the application's build stage is `FROM --platform=$BUILDPLATFORM`, so the jar is compiled once on the runner's own platform. Neither runtime stage has a `RUN` step, so the arm64 image is assembled by copying files, without executing anything for arm64.
- **No build code on a host that holds a write token:** the application's Maven runs inside a BuildKit container, which sees neither the token nor the registry login; the authorization service's Maven runs in `package-auth`, which has no write permission.
- **Clean builds:** no Maven cache in `package-auth` and no BuildKit cache in the image jobs. Every job of a `master` run gets a cache token that can write, so a cache would let the Maven plugins of `package-auth`, or Trivy in `scan`, plant layers that the next publish copies into an image. Each publish starts from Maven Central and the base images only.
- **One publish at a time:** the runs of `master` share one concurrency group (one per ref, not per commit). A running publish is never cancelled, and a newer run replaces only a run that is still waiting, so two publishes never race on `edge`.
- **Tags:** `edge` follows `master` and is rebuilt every Monday, so fixes to the JRE and Alpine reach the image without a code change; `sha-<commit>` marks each push that gets its own run; a `vX.Y.Z` tag adds `X.Y.Z`, `X.Y` and `latest`.
- **Metadata:** title, description and license go in the labels and in the annotations of both the manifests and the index, because GHCR reads a multi-platform image's description from the index.
- **Non-root:** the application image now runs as UID 185, the same unprivileged user as the authorization image.
- **Trivy from its release binary:** version 0.76.2, downloaded from the GitHub release and checked against a SHA-256 written in the workflow. That hash was verified once against the release's Sigstore bundle:

  ```bash
  cosign verify-blob --bundle trivy_0.76.2_Linux-64bit.tar.gz.sigstore.json \
    --certificate-identity 'https://github.com/aquasecurity/trivy/.github/workflows/reusable-release.yaml@refs/tags/v0.76.2' \
    --certificate-oidc-issuer https://token.actions.githubusercontent.com \
    trivy_0.76.2_Linux-64bit.tar.gz
  sha256sum trivy_0.76.2_Linux-64bit.tar.gz
  ```

  The registry login comes from `docker/login-action`, which writes a credential for `ghcr.io` only, never from `TRIVY_USERNAME`, which Trivy would send to every registry it contacts, including the mirror of its database.
- **Report only:** the scan never fails the run. Findings live in the Security tab.

## Alternatives considered

- **Amazon ECR.** It sits next to the demo, but a public image needs ECR Public, and publishing from CI needs an AWS role trusted by GitHub's OIDC provider. GHCR publishes with the workflow's own short-lived token, links each package to the repository and serves public images for free. The cost is that the demo instance pulls over the internet, by IPv4, since `ghcr.io` has no IPv6 address.
- **QEMU emulation for arm64.** It would run Maven under emulation, several times slower, to produce a jar that is identical on both platforms.
- **One native runner per platform, merged into an index.** Two build jobs, digests passed between jobs and an `imagetools create` step, for no gain while the runtime stages execute nothing.
- **`aquasecurity/trivy-action`.** In March 2026 an attacker with stolen credentials force-pushed 76 of the action's 77 tags and published a malicious Trivy 0.69.4 (GHSA-69fq-xp46-6x23, CVE-2026-33634). Pinning the action by SHA freezes the action's code, not the Trivy release it installs; pinning the binary by a hash checked against its signature fixes exactly the code that runs, with fewer parts.
- **A build cache in the GitHub Actions cache.** Faster, but every job of a `master` run can write it, including the ones that run Maven plugins and Trivy (see Clean builds).
- **A registry cache in GHCR**, which only the `publish` token can write. It keeps most of the speed, but adds a `buildcache` tag to each public package and still feeds every publish with whatever the previous one left.
- **Failing the publish on findings.** A HIGH finding in a base image without a fix would block every publish, including the weekly rebuild that brings the fix. Blocking belongs in a branch ruleset over code scanning results, which can come later.
- **Provenance and SBOM attestations.** Left out for now (`provenance: false`, `sbom: false`); they need their own decision on signing and verification.

## Consequences

- Without a cache, each publish resolves every Maven dependency and pulls both base images again, a few minutes more per run in exchange for an image that only the sources and the base images can shape.
- A push that arrives while a publish runs and is then replaced by a newer push never gets its `sha-<commit>` tag. A re-run keeps its original commit, so re-running an older `master` run moves `edge` back to it until the next push or the weekly rebuild; only the newest run should be re-run.
- Pull requests that touch `backend/**` spend a few extra minutes building both images. The workflow is not a required check, so its `paths` filter in `on:` does not risk a pending check (ADR 0001).
- Splitting `package-auth` protects the token, not the integrity of the image: a compromised dependency of the authorization service's build still ends up inside the image it publishes.
- The `publish` token, and the registry login it becomes, are still within reach of the job's four Docker actions (`setup-buildx`, `login`, `metadata` and `build-push`). They are pinned by SHA and are the part of the chain this repository has to trust.
- Trivy reads Alpine's packages and the jars, but not the Temurin JRE, which the base image installs from a tarball, outside any package manager. The JRE's own vulnerabilities stay invisible to the scan; the weekly rebuild of `edge` is what keeps the JRE current.
- GitHub disables scheduled workflows in a public repository after 60 days without activity, and the weekly rebuild stops with it until someone re-enables the workflow.
- A release's images are rebuilt only if its tag workflow is re-run; `edge` is the only tag that moves on a schedule.
- The application has two Dockerfiles: the one in its module is canonical and builds the published image, while `infra/docker/Dockerfile.backend` builds the local Compose stack and can drift from it.
- The authorization image carries no JWT key pair (`*.pem` stays out of git), so every deployment supplies the keys at runtime.
- GHCR creates a pushed package as private, and the workflow's own token can still pull it, so CI does not notice. After the first publish from `master`, each package is made public once in its settings (Package settings, Danger Zone, Change visibility); until then an anonymous pull is denied. GitHub does not let a public package become private again.

## References

- `.github/workflows/images.yml`: the six jobs, their permissions and the Trivy pin.
- `backend/vortex-application-service/Dockerfile` and its `.dockerignore`, an allowlist of what the build copies.
- `backend/vortex-authorization-service/src/main/docker/Dockerfile.jvm`: unchanged, fed by `package-auth`.
- `CLAUDE.md`, section "CI (GitHub Actions)": the images workflow and the rule about write tokens.
- GHSA-69fq-xp46-6x23: https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23
