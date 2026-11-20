# 10. SonarCloud CI-based analysis in one workflow, with coverage for every language

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

SonarCloud ran in automatic analysis since the project was imported. On 2026-11-19 the master analysis carried four warnings: Python analyzed as compatible with 3.10 to 3.15, test-looking Python files analyzed as production code because `sonar.tests` was unset, no AWS provider version for the Terraform rules, and a `tsconfig.json` whose `extends` could not be resolved.

Facts that shaped the decision, as of 2026-11-19:

- Automatic analysis reads only a short list of keys from `.sonarcloud.properties` (sources, tests, inclusions, exclusions, encoding, CPD exclusions and the Python version). It ignored `sonar.issue.ignore.multicriteria`, measured when the three `plsql:VarcharUsageCheck` false positives stayed open after the file landed in pull request #39, and it has no key for `sonar.terraform.provider.aws.version`.
- It never installs dependencies, so the front of the application service, whose `tsconfig.app.json`, `tsconfig.node.json` and `e2e/tsconfig.json` extend `@vue/tsconfig` and `@tsconfig/node26`, is always analyzed without those presets.
- It did not index Java at all (`java` was missing from `ncloc_language_distribution`), so no Java rule ever ran on either backend, and it imports no coverage.
- The project uses the built-in *Sonar way* gate, which includes coverage on new code of at least 80% (applied from 20 new lines). With no coverage data that condition was never evaluated.
- New code is *previous version*, and no analysis had ever set a version, so new code was everything since 2026-11-08.
- The ruleset requires the `SonarCloud Code Analysis` check on every pull request, Dependabot's included (ADR 0005).

## Decision

- **CI-based analysis** in `.github/workflows/sonar.yml`, with automatic analysis switched off in the project (Administration, Analysis Method). `.sonarcloud.properties` is gone; `sonar-project.properties` at the root holds every setting, the multicriteria exclusion included.
- **One SonarCloud project** for the whole repository, as before, so the issue history and the required check stay the same.
- **Build jobs without secrets** (`sonar-java-app`, `sonar-java-auth`, `sonar-front-app`, `sonar-front-auth`, `sonar-data`) run in parallel on every pull request and every push to `master`, with no paths filter, and hand classes, libraries (`dependency:copy-dependencies`) and coverage reports to `sonar-scan` as artifacts. `sonar-scan` is the only job that sees `SONAR_TOKEN`; it runs no build code, only `npm ci --ignore-scripts` so the tsconfig presets resolve, and the scanner with full git history, so SCM blame keeps issues on old lines out of new code.
- **Coverage for every language:** JaCoCo XML from both services, lcov from Vitest's v8 provider in both fronts (written with paths from the repository root through `projectRoot`), and `coverage.xml` from pytest. Only entry points, configuration files and type declarations are excluded from coverage.
- **Tests as tests:** `src/test` of both services, `e2e/` and `__tests__/` of both fronts and `data/pipeline/tests` go to `sonar.tests` and are excluded from the sources.
- **Generated code out:** the OpenAPI client in `frontend/vortex-application-service/src/api/generated/` is excluded.
- **Language settings:** `sonar.python.version=3.12` (the `requires-python` range) and `sonar.terraform.provider.aws.version=6.73` (the version locked in every AWS stack).
- **Version:** `sonar.projectVersion` follows the release version, so new code on `master` restarts at each release.
- **Token:** a personal access token of the owner, valid for one year, stored as `SONAR_TOKEN` in both the Actions and the Dependabot secrets. Organization-scoped tokens need a paid plan.

## Alternatives considered

- **Keep automatic analysis and live with the warnings:** the Python version and the tests could be set there, but the Terraform version, the multicriteria exclusion, the tsconfig presets, Java and coverage cannot.
- **Run the scan inside the existing per-service workflows:** each build job is skipped when its folder did not change, so a single analysis would miss the other languages' data, and one project analyzed by several scans keeps only the last one.
- **One SonarCloud project per service:** the documented monorepo layout, but it splits the history, and the ruleset would need one required check per project.
- **The Maven scanner:** it reads the classpath by itself, but it analyzes one Maven project, and the repository has two plus three non-Java modules.
- **Secretless analysis (`sonarqube-auth-action` over OIDC):** no stored secret, but its behaviour on Dependabot pull requests is not documented, and a required check that never reports blocks every merge.

## Consequences

- The four analysis warnings are gone, and the Java rules run for the first time. Issues they find on lines untouched since the last change count as overall code, not new code.
- The 80% coverage condition now applies to the changed lines of every pull request with 20 or more new lines. The JaCoCo floors in the poms are still the build gate for the whole module.
- Every pull request runs both Maven builds and both front test suites a second time, about ten minutes of runner time that does not delay the per-service checks, which run in parallel.
- Pull requests from forks get no secret, so their scan fails. The token expires after a year and must be renewed in both secret stores before that.
- The version must be bumped in `sonar-project.properties` together with the other modules (CLAUDE.md, Governance).

## References

- SonarQube Cloud, *Automatic analysis*: https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/automatic-analysis/
- SonarQube Cloud, *GitHub Actions*: https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/ci-based-analysis/github-actions-for-sonarcloud/
- SonarQube Cloud, *Test coverage parameters*: https://docs.sonarsource.com/sonarqube-cloud/enriching/test-coverage/test-coverage-parameters/
- SonarQube Cloud, *Quality gates*: https://docs.sonarsource.com/sonarqube-cloud/improving/quality-gates/
- SonarSource/sonarqube-scan-action: https://github.com/SonarSource/sonarqube-scan-action
