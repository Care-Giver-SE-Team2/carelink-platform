# CareLink Platform

A cloud-native platform for community home care. Care managers, caregivers, families, elders
and value-added service providers work on one platform: care plans, rostering, visits,
incidents, reports and notifications are platform services that the web applications, and the
providers' own systems, build on.

> **New to the code?** Read [ARCHITECTURE.md](ARCHITECTURE.md) first. It explains how a service
> is laid out inside (modules, the four layers, where interfaces go) and which rules the build
> rejects.

---

## Status

| Part | State |
|---|---|
| `services/core` | The care domain as one modular service: identity, profile, care plan, rostering, visit, incident, report. visit and report move out into services of their own next |
| `services/notification` | The in-app inbox, the first module moved out of core and the worked example for the next ones |
| Build, image, local run | One way for every service: the parent `pom.xml`, `build/Dockerfile`, `build/entrypoint.sh`, `scripts/build.sh`, `scripts/run.sh` |
| Deployment | One Helm chart and `scripts/deploy.sh`; the cloud environment (Terraform, `infra/`) comes next |
| Pipeline | Build, tests, SonarCloud, dependency scan and image for every changed service. Pushing to ECR, staging, the ZAP scan, approval and demo are added with the cloud environment |

---

## Technology

| Layer | Choice |
|---|---|
| Front end | React 19, Vite 8, TypeScript 6 |
| Services | Java 25, Spring Boot 4.1 |
| Database | MySQL 8.4, schema owned by Flyway |
| Testing | JUnit 5, ArchUnit, Testcontainers, Vitest |
| Packaging and deployment | Docker, Helm, Kubernetes (Amazon EKS) |
| Pipeline | GitHub Actions, SonarCloud, Trivy, gitleaks, OWASP ZAP |

---

## Repository map

```
carelink-platform/
├─ services/
│  ├─ core/                       pom.xml, src/, deploy/values.yaml: every service has this shape
│  └─ notification/               the in-app inbox: the first service moved out of core
├─ libs/
│  ├─ shared/                     core's shared package for every service: errors, request context, access audit, roles
│  ├─ platform-security/          who is signed in, read from the session core wrote; the security chain of every service but core
│  ├─ core-api/                   core's internal API (/internal/v1) as a Java interface, and its client
│  ├─ events/                     events between services: the outbox, its relay to SNS, the SQS consumer
│  └─ test-support/               shared test helpers: MySQL and Redis containers, the exception handler
├─ build/
│  ├─ Dockerfile                  the Dockerfile of every service: Maven build, then a JRE, non-root
│  └─ entrypoint.sh               the start-up of every service: JVM settings, time zone, DNS cache, graceful stop
├─ charts/carelink-service/       the Helm chart of every service: Deployment, Service, autoscaling,
│                                 probes, disruption budget, spread over availability zones
├─ scripts/
│  ├─ build.sh  <service|all>         compile, test, image
│  ├─ run.sh    <service|all|down>    run locally with docker compose
│  ├─ deploy.sh <service|all> <env>   deploy to staging or demo with Helm
│  └─ localstack/events.sh            the events topic and queues LocalStack creates for a local run
├─ .github/workflows/
│  ├─ ci.yml                      works out what a commit changed and runs service.yml for those services
│  └─ service.yml                 the pipeline every service goes through
├─ frontend/                      the React web application
├─ docs/                          the API contract (docs/api/), data models, design notes
├─ pom.xml                        parent of every service: Java version, dependency versions, test and coverage plugins
├─ docker-compose.yml             the local environment: the services plus MySQL, Redis and LocalStack
└─ ARCHITECTURE.md                inside a service: modules and layers
```

The infrastructure code (`infra/`, Terraform) and the load tests (`loadtest/`, k6) join this
layout as they are written.

---

## Getting started

Prerequisites: **JDK 25**, **Docker** with Compose v2, **Node 22**. For deployment also Helm,
kubectl and the AWS CLI. On Windows, run the scripts from Git Bash.

```bash
# core with its database and Redis, on http://localhost:8080
scripts/run.sh core

# the front end, on http://localhost:5173 (/api is proxied to port 8080)
cd frontend && npm ci && npm run dev

# stop everything (the database volume stays)
scripts/run.sh down
```

The first `run.sh` writes random local database passwords to `.env`, which git ignores; no
password is ever committed. `scripts/run.sh all` also starts LocalStack (SQS, SNS).

Everyday commands:

```bash
scripts/build.sh core test       # unit, architecture and MySQL integration tests (needs Docker)
scripts/build.sh core            # compile, test and build the image
scripts/build.sh all             # the same for every service

cd frontend
npm run lint
npm run test                     # watch mode
npm run test:coverage            # single run with coverage
```

---

## One way to build, run and deploy every service

Every service is built, started, deployed and checked the same way. What is shared is written
once; a service supplies only its code and one values file.

| Concern | Shared, written once | Each service supplies |
|---|---|---|
| Compile and test | `pom.xml` (parent): Java version, dependency versions, test and coverage plugins; `scripts/build.sh` | its code and tests |
| Image | `build/Dockerfile`, given the service name; tagged with the commit | — |
| Start-up | `build/entrypoint.sh`: the same JVM settings, time zone, DNS cache time and graceful shutdown; configuration from environment variables; health from Spring Boot's liveness and readiness endpoints | the environment variables it needs |
| Local run | `docker-compose.yml` and `scripts/run.sh`, from the same images the cloud runs | an entry in `docker-compose.yml` |
| Deployment | `charts/carelink-service` and `scripts/deploy.sh` | `deploy/values.yaml`: replicas, resources, scaling |
| Pipeline | `.github/workflows/service.yml`, called by `ci.yml` | its name (its directory) |

**Adding a service:** create `services/<name>/` with a `pom.xml` whose parent is the root
`pom.xml`, its `src/`, and `deploy/values.yaml`; add it to `<modules>` in the root `pom.xml`
and to `docker-compose.yml`. The pipeline finds it by its directory, and the scripts take its
name. [docs/platform/building-a-service.md](docs/platform/building-a-service.md) walks through
it, including the signed-in user (`libs/platform-security`) and calls to core (`libs/core-api`).

A change to a shared part reaches every service, so it goes through review like any other
change, and the pipeline rebuilds every service when one is touched.

---

## Pipeline

`ci.yml` is the entry point. It works out which services a commit touched and runs
`service.yml` once for each of them; a change to a shared part runs it for all of them.

| Step | What runs | How |
|---|---|---|
| 1. Build | compile and package | `scripts/build.sh <service> compile` |
| 2. Tests | unit tests, ArchUnit layering rules, MySQL integration tests (Testcontainers), JaCoCo with at least 80% line coverage in domain packages | `scripts/build.sh <service> test` |
| 3. SonarCloud | static analysis and the quality gate | one project per service |
| 4. Dependency scan | Trivy; a HIGH or CRITICAL finding with a fix available fails the run | findings in the Security tab |
| 5. Image | `build/Dockerfile`, tagged with the commit | `scripts/build.sh <service> image` |
| 6 to 10 | push to ECR (scanned on push), deploy to staging, smoke test and ZAP baseline scan, approval, deploy the same image to demo | added with the cloud environment |

Alongside, for the whole repository: the front end (lint, Vitest with coverage, build) when
`frontend/` changes, and secret scanning (gitleaks, across the whole history) on every run.
The pipeline runs on every pull request and push to `main`, and nightly to catch newly
published vulnerabilities in existing dependencies.

**Quality gate** is the one check branch protection requires. It passes when nothing that ran
has failed, however many services ran.

Repository settings the pipeline reads:

| Setting | Kind | Purpose |
|---|---|---|
| `SONAR_TOKEN` | secret | SonarCloud analysis; without it step 3 is skipped with a notice |
| `SONAR_ORG` | variable | the SonarCloud organisation |
| `SONAR_PROJECT_PREFIX` | variable | project keys are `<prefix>_<service>` |

Access to AWS will use GitHub's OIDC token exchanged for a short-lived IAM role, so no AWS key
is stored in the repository or its settings.

---

## Branching

`main` is protected: changes arrive by pull request, with one approving review and a green
Quality gate. Branch names: `feat/<service>-<summary>`, `fix/<summary>`.
