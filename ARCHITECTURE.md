# CareLink Architecture

**Read this before writing any code.** It explains how the codebase is laid out, why it
is laid out that way, and what the build will reject.

> **Feature modules are not decided yet.** This document specifies what happens *inside*
> a module: how it is layered, where interfaces go, which way dependencies point. None of
> that depends on where the module boundaries end up. Once the boundaries are agreed,
> create packages that follow the skeleton below.
>
> Today the repository contains `shared` (common concerns) and `identity`
> (authentication, used by every module). **`identity` is the reference implementation**
> — open it alongside this document.

---

## 1. If you have only ever seen `controller / service / repository`

Most Spring tutorials and most student projects use a three-layer split:

```
com.example.app/
├─ controller/     HTTP entry points
├─ service/        business logic
├─ repository/     data access
└─ entity/         JPA entities
```

This project uses four layers instead. Here is the exact mapping:

| Familiar name | Here | What actually changed |
|---|---|---|
| `controller/` | `controller/` | Nothing. Same idea, same name. |
| `service/` | `application/` | **Renamed, and narrowed.** It orchestrates a use case; it does not hold business rules. |
| `repository/` (interface + impl together) | `domain/repository/` (**interface**)<br>`infrastructure/persistence/` (**implementation**) | **Split in two.** |
| `entity/` (JPA entity doubling as the business object) | `domain/model/` (**plain Java**)<br>`infrastructure/persistence/` (**JPA entity**) | **Split in two.** |
| — | `domain/service/` | New. Rules that span several entities. |

**The word `repository` has not gone anywhere** — it is `domain/repository/`. Only two
things genuinely changed: the repository and the entity are each split into an
abstraction the domain owns and an implementation the persistence layer owns.

That one split is what buys the property the proposal promises: *"business rules sit only
in the domain layer, so they can be tested without spinning up the full application or a
database."*

### Why `application` and not `service`

Because in the three-layer world `service` **means** "the place business logic goes".
Reusing the word while changing the rule would mislead every reader. `application` is
unfamiliar on purpose: it makes people look up what belongs there. Business rules go in
`domain`; `application` only orchestrates.

---

## 2. Layout

```
services/core/src/main/java/sg/nus/carelink/
├─ CareLinkApplication.java
│
├─ shared/                   common concerns, owned by no single module
│  ├─ security/              SecurityConfig, Role
│  ├─ error/                 BusinessRuleViolation, ResourceNotFound
│  │                         (plain Java, so the domain layer may depend on it)
│  ├─ web/                   GlobalExceptionHandler (one ProblemDetail shape),
│  │                         RequestContextInterceptor + WebMvcConfig (login check,
│  │                         X-Request-Id, user in every log line, access log)
│  ├─ config/                CareLinkProperties, business thresholds in one place
│  ├─ audit/                 auditing (to be built)
│  └─ scheduling/            scheduled-task support (to be built)
│
├─ identity/                 reference implementation, all four layers present
│  ├─ controller/            (1) presentation: AuthController + dto/
│  ├─ application/           (2) application: IdentityService, UserDirectory
│  ├─ domain/
│  │  ├─ model/              (3) domain model: AppUser
│  │  └─ repository/         (3) domain ports: AppUserRepository
│  └─ infrastructure/        (4) implementations
│     ├─ persistence/
│     │  ├─ entity/          AppUserJpaEntity
│     │  ├─ repository/      AppUserJpaRepository (Spring Data)
│     │  └─ adapter/         AppUserMapper, AppUserRepositoryAdapter (implements the port)
│     └─ security/           UserDetailsService, authentication query
│
├─ profile/                  elder, caregiver, family member, binding, intake, credentials
├─ careplan/                 care plan tree and required credentials
├─ rostering/                availability, absences, rostering runs and constraint checks
├─ visit/                    visit state machine, tasks, vitals, evidence, elder confirmation
├─ incident/                 incidents, escalation log, family acknowledgement, spot checks
├─ report/                   reports, value-added services, periodic caregiver reviews
├─ notification/             subscriptions and notifications
│     each has exactly identity's folders and, per table, the same set of classes:
│     domain/model/<X> (record), domain/repository/<X>Repository (port),
│     persistence/entity/<X>JpaEntity, persistence/repository/<X>JpaRepository,
│     persistence/adapter/<X>Mapper + <X>RepositoryAdapter; plus one <Module>Service and
│     <Module>Controller. All generated from V2__care_domain.sql as a compiling, tested
│     starting point; the design work (aggregates, behaviour, use cases) is the owner's
│
└─ shared/audit/persistence/ entity/ + repository/ for audit_log (cross-cutting, so it lives in shared)
```

The module split above follows the sections of the schema. It is a starting point, not a
verdict: moving a package is a one-line IDE refactor, and the slice rule in
LayerDependencyTest will tell you if the move created a cycle.

Outer level splits by feature, inner level splits by layer. Not the other way round: there
is no top-level `controller/` package holding every controller in the system.

### What each layer may contain

| Layer | Put here | **Never** put here |
|---|---|---|
| `controller` | REST controllers, request/response DTOs, format validation, status codes | Any business rule, any SQL |
| `application` | Use-case orchestration, transaction boundaries, cross-module contracts | The business rules themselves |
| `domain` | Entities, value objects, domain services, repository **interfaces**, design patterns | JPA annotations, Spring Data, any import from the two layers above |
| `infrastructure` | JPA entities, repository **implementations**, external-service adapters | Business rules |

The domain layer is the important one. Design problems and design patterns live there;
the analysis and design diagrams in the report describe that layer. Because it depends on
neither Spring nor a database, its tests run in milliseconds.

### Talking to another module

- Never import another module's `domain` or `infrastructure`.
- Call the interface that module exposes from its `application` package.
- `identity/application/UserDirectory` is exactly such a contract.

---

## 3. Where interfaces go

The most common mistake in a layered Java project is pairing every service with an
`XxxService` + `XxxServiceImpl`. **We do not do that.** An interface with one
implementation that nobody outside the module calls is ceremony, not abstraction.

Interfaces appear in four situations, each with a concrete reason:

| Purpose | Interface lives in | Implementation lives in | Why it must be an interface |
|---|---|---|---|
| **Repository** | `domain/repository/` | `infrastructure/persistence/` | Dependency inversion. The domain says what it needs without knowing JPA exists. **This is the only reason the domain layer can be tested without a database.** |
| **Polymorphic point of a design pattern** | `domain/model/` or `domain/service/` | Sibling classes in the same package | A design problem *is* one interface with several implementations — State, Strategy, Chain of Responsibility. |
| **Port to an external service** | `domain/port/` | `infrastructure/adapter/` | When a language model is unavailable, a rule-based implementation takes over. The interface is that substitution point. |
| **Cross-module contract** | `application/` | A service class in the same module | Other modules import the interface rather than your implementation, so refactoring your internals cannot break them. |

Everything else is a plain class. `IdentityService` in this repository has no
`IdentityServiceImpl`.

<details>
<summary>Why the traditional "interface for every service" convention is obsolete</summary>

It was a **technical requirement**, not a matter of taste. Early Spring could only build
transaction proxies with JDK dynamic proxies, and a JDK dynamic proxy **requires an
interface** — without one, `@Transactional` silently did nothing. Add EJB's mandatory
Home/Remote interfaces and the fact that early Mockito could not mock concrete classes,
and the `Impl` suffix became a habit.

None of those constraints survive. Spring Boot 2 onwards defaults to CGLIB class proxies,
so `@Transactional` works on a concrete class. Mockito 5's inline mock maker can mock even
final classes. EJB is long retired. **The habit outlived its reason.**

(As an aside: the GoF line "program to an interface, not an implementation" was written in
1994, a year before Java existed. "Interface" there means the public type, and a class is
a type too. It never asked for a twin interface per class.)

The real cost of a 1:1 interface: every signature written twice, every change made in two
files; Ctrl-click lands on the interface instead of the code; and it broadcasts a false
signal — a reader sees an interface and goes looking for the other implementations, of
which there are none.

There is also a project-specific reason. The report requires each member to argue one
design problem and the pattern chosen for it. If every service has an interface, then an
interface carries no information and a reader cannot tell a design decision from
boilerplate. Under this rule, **every `interface` in the codebase is a decision someone
can defend.**

If the team ultimately prefers the traditional style, then adopt it everywhere.
Consistency matters more than this preference; five people writing five styles is the
worst outcome.
</details>

---

## 4. Reference implementation: the `identity` module

Real code in this repository. Copy this skeleton for a new module.

```
identity/
├─ controller/                             (1) presentation
│  ├─ AuthController.java                  login, current user; HTTP only
│  └─ dto/
│     ├─ LoginRequest.java                 @NotBlank format validation
│     └─ CurrentUserResponse.java          the front end picks its screens from roles
│
├─ application/                            (2) application
│  ├─ IdentityService.java                 a class; no Impl, because there is one implementation
│  ├─ UserDirectory.java            [interface] cross-module contract
│  └─ UserDirectoryService.java            implements UserDirectory
│
├─ domain/                                 (3) domain - plain Java
│  ├─ model/
│  │  └─ AppUser.java                      holds no password hash; see below
│  └─ repository/
│     └─ AppUserRepository.java     [interface] the domain states what it needs
│
└─ infrastructure/                         (4) persistence
   ├─ persistence/
   │  ├─ AppUserJpaEntity.java             JPA annotations, kept apart from the domain model
   │  ├─ AppUserJpaRepository.java         Spring Data; never exposed outside this package
   │  ├─ AppUserMapper.java                JPA entity to domain model, one direction
   │  └─ AppUserRepositoryAdapter.java     implements the domain's AppUserRepository
   └─ security/
      ├─ AuthenticationQuery.java    [interface] narrow read port for authentication
      ├─ JpaAuthenticationQuery.java       implementation
      └─ JpaUserDetailsService.java        adapts to Spring Security's UserDetails
```

### Dependency inversion, concretely

```java
// domain/repository/AppUserRepository.java - the domain states what it needs
public interface AppUserRepository {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findById(Long id);
}
```

```java
// infrastructure/persistence/AppUserRepositoryAdapter.java - persistence provides it
@Repository
class AppUserRepositoryAdapter implements AppUserRepository {
    private final AppUserJpaRepository jpa;
    public Optional<AppUser> findByUsername(String username) {
        return jpa.findByUsername(username).map(AppUserMapper::toDomain);
    }
}
```

The arrow points `infrastructure -> domain`, not the other way. That is why a test can
supply a twenty-line fake and exercise the application layer with **no Spring and no
database** — see `src/test/.../identity/application/InMemoryAppUserRepository.java`.

### Starting a module from the generated entities

Every table already has the whole vertical slice, generated and tested: a domain record,
a repository port, the JPA entity (validated column by column against the schema at
start-up, `ddl-auto: validate`), the Spring Data repository, and the mapper + adapter that
implement the port. Each module also has a `<Module>Service` with one lookup use case and
a `<Module>Controller` with one endpoint, so the wiring can be seen end to end. Everything
is deliberately plain: ids instead of `@ManyToOne` (so modules never import each other's
classes), enums nested, no business logic yet. What the owner changes, in this order:

1. `domain/model/` — the aggregate as ordinary Java with the business rules
   (`AppUser` in identity is the template); the domain enums belong here too.
2. `domain/repository/` — the port the application layer talks to.
3. `infrastructure/persistence/adapter/` — a mapper and a repository adapter that implement
   the port using the generated entity, as `AppUserMapper` and `AppUserRepositoryAdapter` do.
4. `application/` and `controller/` — the use cases and the HTTP surface.

A change to a table is a new Flyway script (`V3__…`) followed by the matching edit to the
entity; the application refuses to start if the two disagree.

### Two decisions worth copying

**The domain model holds no password hash.** A password is an implementation detail of
authentication. When authentication needs the hash it goes through its own narrow port,
`AuthenticationQuery`, so the domain model stays about identity and permissions.

**`shared/security/SecurityConfig` imports nothing from `identity`.** The
`UserDetailsService` is injected by type. That keeps the dependency `identity -> shared`
one-way, so the two never form a cycle — which rule 4 below would reject.

**Cross-cutting request handling lives in `shared/web`, not in controllers.** Spring
Security's filter chain decides whether a request may enter at all. Behind it,
`RequestContextInterceptor` runs around every `/api/**` call except `/api/auth/login`: it
refuses anything without an authenticated user (defence in depth), returns an
`X-Request-Id`, puts the request id and username into the logging MDC, and writes one
access-log line per request. A controller therefore never checks "is someone logged in"
and never logs who called it — both are already done. Which *role* may call an endpoint is
declared on the method with `@PreAuthorize("hasRole('MANAGER')")`.

---

## 5. The build enforces this

`services/core/src/test/java/sg/nus/carelink/architecture/LayerDependencyTest.java` (ArchUnit)
runs in the fast stage and takes seconds.

| Rule | What it stops |
|---|---|
| `domain` must not depend on `..controller..` or `..infrastructure..` | Dependencies flowing outwards, leaving the domain hostage to the web layer and the database |
| `controller` must not depend on `..infrastructure..` | Controllers bypassing the application layer to reach a repository implementation |
| `domain` must not depend on `jakarta.persistence` or `org.springframework.data` | A domain model contaminated by JPA, forcing every test to start a database |
| Module slices must be free of cycles | Modules entangling until the codebase is a single ball of mud |

Break one and the pull request goes red. This is not left to code review, and it is the
non-functional requirement the proposal commits to: *the build fails if the domain layer
references the presentation layer or a persistence implementation.*

There is a fifth gate waiting in `pom.xml`: a JaCoCo rule failing the build below **80%
line coverage in domain packages**. It is commented out while there is no domain code —
it must be switched on once feature modules have logic, because the proposal promises it.

---

## 6. Front end

The front end is not four applications. It is one React codebase serving four kinds of
user, so it splits along two axes: **features by module, pages by role.**

```
frontend/src/
├─ shared/
│  ├─ api/                    API client, one file per backend module
│  ├─ components/             shared components
│  ├─ theme/
│  │  ├─ standard.ts          manager, caregiver, family
│  │  └─ elder.ts             elder: large type, high contrast, large touch targets
│  └─ auth/                   sign-in, role context, route guards
│
├─ features/<module>/         one per backend module, one owner each
│
└─ routes/                    pages assembled per role
   ├─ manager/                desktop layout, dense
   ├─ caregiver/              mobile first
   ├─ family/                 mobile first
   └─ elder/                  large buttons, icon led
```

Each person writes their own `features/<module>/` components and
`shared/api/<module>.ts`, then places their pages under `routes/<role>/`. A role directory
holds files from several authors, but no two people edit the same file.

---

## 7. In one sentence

> Your code lives in four places: `services/core/.../<your module>/`,
> `services/core/src/test/.../<your module>/`, `frontend/src/features/<your module>/`, and
> `frontend/src/routes/<role>/<your page>.tsx`.
>
> Before touching `shared/` or changing the schema, say so in the group chat.
