# Building a service

How a module of core becomes a service of its own. It is written for the owners of visit, report and
notification, and covers five things: the shape every service has, how a service knows who is signed in, how it
calls core, how it sends and receives events, and what it may do with the database before the schema split.

What moves, and what replaces each call into core, is in [service-boundaries.md](service-boundaries.md). This
guide covers how. Inside a service, the modules and layers follow [ARCHITECTURE.md](../../ARCHITECTURE.md), as
in core.

**Worked example: `services/notification`.** It was the first module moved out, and it has every part this guide
describes: the pom, the main class, both settings files, the test settings, the values file, the compose entry
and the front end's proxy entry. Start a new service by copying it.

---

## 1. What every service has

| Part | Where | Written once, shared |
|---|---|---|
| Build | `services/<name>/pom.xml`, parent is the root `pom.xml` | Java version, dependency versions, test, coverage and Sonar settings |
| Code and tests | `services/<name>/src/` | — |
| Settings | `src/main/resources/application.yml` and `config/application.yml` | — |
| Image | — | `build/Dockerfile` and `build/entrypoint.sh`, given the service name |
| Local run | an entry in `docker-compose.yml` | `scripts/run.sh` |
| Deployment | `services/<name>/deploy/values.yaml` | `charts/carelink-service`, `scripts/deploy.sh` |
| Pipeline | — | `ci.yml` finds the service by its directory and runs `service.yml` for it |
| Errors, request context, audit | dependency on `libs/shared` | core's `shared` package: error types and the exception handler, request id and access log, `audit_log` writes, roles |
| Signed-in user | dependency on `libs/platform-security` | the security chain and `SignedInUsers` |
| Calls to core | dependency on `libs/core-api` | the `CoreApi` client |
| Calls to visit | dependency on `libs/visit-api` | the `VisitApi` client; core serves it until visit moves out |
| Events | dependency on `libs/events` | the outbox and its relay to SNS, the SQS consumer that handles each event once |

## 2. Step by step

### 2.1 Move the code

Move the module with `git mv`, in a commit that only moves files, so the history of each class follows it. Keep
the package names (`sg.nus.carelink.visit…`).

The classes the module uses from core's `shared` package are in `libs/shared`, in the same packages: the error
types, the exception handler, the request context (request id, user and access log), the access audit and the
roles. Depend on the library instead of copying them, so every service answers errors the same way. Core's
login (`shared.security.SecurityConfig`) and its page controllers stay in core. A service gets its security from
`platform-security` (section 3); a security chain of its own would switch that library off.

Some beans the module relies on are declared by another module of core. `libs/shared` supplies the `Clock` when
the service has none. Others, such as `@EnableScheduling`, the service declares itself. A start-up failure with
"No qualifying bean" names the one that is missing.

The main class goes in `sg.nus.carelink`, the same as core's `BackendApplication`, so component scanning covers
every module package:

```java
package sg.nus.carelink;

@SpringBootApplication
public class VisitApplication {

	public static void main(String[] args) {
		SpringApplication.run(VisitApplication.class, args);
	}

}
```

### 2.2 `pom.xml`

Start from core's `pom.xml` and keep only what the service uses. Most services need about this much:

```xml
<parent>
	<groupId>sg.nus</groupId>
	<artifactId>carelink-platform</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<relativePath>../../pom.xml</relativePath>
</parent>
<artifactId>visit</artifactId>

<dependencies>
	<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
	<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
	<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
	<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
	<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
	<dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
	<!-- core's shared package: errors, request context, audit, roles -->
	<dependency><groupId>sg.nus</groupId><artifactId>shared</artifactId><version>${project.version}</version></dependency>
	<!-- The login core made, read from Redis -->
	<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-session-data-redis</artifactId></dependency>
	<dependency><groupId>sg.nus</groupId><artifactId>platform-security</artifactId><version>${project.version}</version></dependency>
	<!-- Calls to core -->
	<dependency><groupId>sg.nus</groupId><artifactId>core-api</artifactId><version>${project.version}</version></dependency>
	<!-- Events between services, for a service that publishes or handles them -->
	<dependency><groupId>sg.nus</groupId><artifactId>events</artifactId><version>${project.version}</version></dependency>

	<!-- Testing: webmvc-test, security-test, flyway and flyway-mysql (test scope, see 2.5),
	     sg.nus:test-support (test scope), archunit-junit5: as in services/notification/pom.xml -->
</dependencies>

<build>
	<plugins>
		<plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
	</plugins>
</build>
```

Then add `<module>services/<name></module>` to the root `pom.xml`, after the libraries.

### 2.3 Settings

`src/main/resources/application.yml` holds the service's own settings. The database settings come from the same
environment variables as core's:

```yaml
spring:
  application:
    name: visit
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/carelink?connectionTimeZone=Asia/Singapore}
    username: ${DB_USER:carelink}
    password: ${DB_PASSWORD:carelink}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    # Until the schema split, core migrates the one shared schema (section 6); tests: 2.5
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true

info:
  app:
    name: visit
    commit: ${APP_COMMIT:unknown}
```

`src/main/resources/config/application.yml` holds the platform settings, as core has them. Spring Boot reads
this file after `application.yml`:

```yaml
spring:
  data:
    redis:
      repositories:
        enabled: false             # Redis holds sessions only
  session:
    data:
      redis:
        namespace: carelink:session  # must be the same in every service, or core's logins are not seen

carelink:
  core-api:
    base-url: http://localhost:8080  # for a run outside Docker; overridden by CARELINK_COREAPI_BASEURL
```

The Redis address comes from `SPRING_DATA_REDIS_HOST` and `SPRING_DATA_REDIS_PORT`, which default to
`localhost:6379`.

### 2.4 Local run: `docker-compose.yml`

Add an entry like core's. The service starts after core, because core migrates the schema the service
validates. The `CARELINK_EVENTS_*` and `AWS_*` lines are for a service that uses events (section 5):

```yaml
  visit:
    image: ${IMAGE_PREFIX:-carelink}/visit:${IMAGE_TAG:-local}
    depends_on:
      core:
        condition: service_healthy
      localstack:
        condition: service_healthy
    environment:
      DB_URL: jdbc:mysql://mysql:3306/carelink?connectionTimeZone=Asia/Singapore
      DB_USER: carelink
      DB_PASSWORD: ${MYSQL_PASSWORD:?run scripts/run.sh, which writes .env}
      SPRING_DATA_REDIS_HOST: redis
      CARELINK_COREAPI_BASEURL: http://core:8080
      CARELINK_EVENTS_TOPICARN: arn:aws:sns:ap-southeast-1:000000000000:carelink-events
      CARELINK_EVENTS_QUEUEURL: http://localstack:4566/000000000000/carelink-visit
      CARELINK_EVENTS_ENDPOINT: http://localstack:4566
      AWS_ACCESS_KEY_ID: test             # LocalStack takes any key
      AWS_SECRET_ACCESS_KEY: test
    ports:
      - "8081:8080"
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      retries: 60
      start_period: 20s
```

Every service listens on 8080 inside its container. On the host, visit uses 8081, report 8082 and notification
8083.

The front end's development server sends every `/api` path to core (`frontend/vite.config.ts`). When a service
takes over some paths, add a proxy entry for them above the `/api` entry: the first matching entry wins. Tell
the owner of the cloud environment about the same paths, so the Ingress routes them too.

### 2.5 Tests before the schema split

The service never migrates the shared schema, but its integration tests need it. They build it from core's own
migrations, in `src/test/resources/config/application.properties`:

```properties
spring.flyway.enabled=true
spring.flyway.locations=filesystem:../core/src/main/resources/db/migration
# The login travels in MockMvc in tests, as in core's tests
spring.autoconfigure.exclude=org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration
management.health.redis.enabled=false
```

`libs/test-support` gives every service the same test helpers as core:
- `SharedMySql`: one MySQL container for the whole run, with a database of its own for each test class.
- `SharedRedis`: one Redis container for the whole run.
- `GlobalExceptionHandlerTestSupport`: the real exception handler, for standalone MockMvc controller tests.

### 2.6 Deployment: `deploy/values.yaml`

Copy `services/core/deploy/values.yaml` and change what differs: replicas, resources, autoscaling. The service
reaches core inside the cluster at `http://core`, the Service the chart creates for the release `core`:

```yaml
env:
  CARELINK_COREAPI_BASEURL: http://core
```

The cloud environment provides the database and Redis settings, the same way for every service: `envFromSecret`
names the Kubernetes Secret that holds them. It also provides the events topic's ARN and the service's queue URL
(`CARELINK_EVENTS_TOPICARN`, `CARELINK_EVENTS_QUEUEURL`). The pod's IAM role gives the credentials, so no key is
set. The Ingress routes only `/api/**`. Nothing routes `/internal`.

## 3. Who is signed in: `platform-security`

Sign-in happens in core only. Core keeps the login in Redis, and every service reads the same session through
this library. A service that depends on it gets the following, with nothing to configure:

| What | How |
|---|---|
| Every request needs a signed-in user | anonymous: 401; `/actuator/health/**`, `/actuator/info` and `/internal/**` are open |
| CSRF the same as core | the token in the `XSRF-TOKEN` cookie and the `X-XSRF-TOKEN` header; not on `/internal/**` |
| No session of its own | `SessionCreationPolicy.NEVER`; no saved requests |
| No passwords | sign-in in a service is refused |
| `SignedInUsers` | `require()` gives the user's id, username, display name and roles, or answers 401, even when the service's exception handler has a catch-all |

The library applies only when the service has no `SecurityFilterChain` of its own. Do not add one.

**Replacing `IdentityService.require(username)`.** That call goes, and the signed-in user comes from the session:

```java
SignedInUser user = signedInUsers.require();
Long accountId = user.id();          // app_user.id
boolean manager = user.hasRole("MANAGER");
```

**`@PreAuthorize` stays.** The library does not turn method security on, so add `@EnableMethodSecurity` to one
configuration class of the service. The roles in the session are the same as in core.

**Testing a controller on its own.** A `@WebMvcTest` slice does not load the library by itself. Import it, then
give the user the way core leaves them: a security context plus the two session attributes.
`ServiceSecuritySliceTest` in the library runs this setup:

```java
@WebMvcTest(VisitController.class)
@ImportAutoConfiguration(ServiceSecurityAutoConfiguration.class)
class VisitControllerTest {

	@Test
	void aCaregiverSeesTheirVisits() throws Exception {
		mvc.perform(get("/api/caregiver/visits")
						.with(user("ali").roles("CAREGIVER"))
						.sessionAttr(SignedInUserSession.ID, 70L)
						.sessionAttr(SignedInUserSession.DISPLAY_NAME, "Ali Hassan"))
				.andExpect(status().isOk());
	}

}
```

## 4. Calling core: `core-api`

`CoreApi` is core's internal API as a Java interface: each method is one endpoint under `/internal/v1`, and the
records next to it are the requests and answers. Core implements the same interface, and `CoreApiContractTest`
in core checks the two sides against each other on every build. With `carelink.core-api.base-url` set, the
library makes a `CoreApi` bean (connect timeout 2 s, read timeout 5 s).

**Keep your port; change the adapter.** The business code keeps calling the interface it calls today, and only
the class behind it changes:

```java
@Component
class CoreFamilyAccess implements FamilyAccess {   // visit's own port

	private final CoreApi core;

	CoreFamilyAccess(CoreApi core) {
		this.core = core;
	}

	@Override
	public void requireReadableElder(String username, Long elderId) {
		core.checkElderAccess(username, elderId, CoreApi.Access.READ);
	}

}
```

**Errors.** Core's answers come back as exceptions:

| Core answers | The client throws | The service should |
|---|---|---|
| 403 | `AccessDeniedException` | let it through: the service answers 403, as core did |
| 404 | `CoreNotFound` | translate it where the code expects `ResourceNotFound` |
| 409 | `CoreRuleViolation`, with core's `code()` | translate it to `BusinessRuleViolation(code(), getMessage())`, so the caller gets the same code |
| anything else | `RestClientResponseException`; a timeout is a `ResourceAccessException` | let it through: 500 |

For a lookup that may find nothing, use the `find…` methods (`findCaregiverPublicProfile`,
`findFamilyMemberIdByUser`, `findPrimaryCaregiverId`). They return an empty `Optional` instead of throwing.

The client does not retry. A read can simply be called again. A `POST` is repeated only where the caller knows
it is safe. For example, the missed check-in scan tries again on its next run.

### Calling visit: `visit-api`

visit's internal API is built the same way: `VisitApi` in `libs/visit-api`, set up by
`carelink.visit-api.base-url`, with the same timeouts. Its errors come back as `VisitNotFound` and
`VisitRuleViolation`, and the lookups that may find nothing have `find…` methods (`findVisit`,
`findVisitState`, `findLatestCaregiverId`).

Until visit moves out, core serves this API (`InternalVisit*Controller`, checked by `VisitApiContractTest`).
A service that leaves core before visit does, such as report, points `carelink.visit-api.base-url` at core;
once visit runs on its own, the setting points at visit and nothing else changes. Rostering and incident stay in
core: they call visit in-process until it moves, and then their adapters switch to `VisitApi`.
[service-boundaries.md](service-boundaries.md), section 4, lists which call replaces which.

**Testing.** In the service's own tests, replace the client with `@MockitoBean CoreApi core`. The contract
itself is tested in core.

**A new endpoint** is one change in core: the method in `CoreApi`, the controller method in core, and a case in
`CoreApiContractTest`. Ask the owner of core, or open the pull request and ask them to review it.

## 5. Events between services: `events`

A service tells the others what happened by publishing an event, and the services that care handle it. Events
travel through one SNS topic. Each service has its own SQS queue subscribed to the topic, with a dead-letter
queue behind it. [event-catalogue.md](event-catalogue.md) lists which events exist, who publishes each, what it
carries, and who handles it.

**Publishing.** Call `Events.publish` in the transaction that changes the data the event is about:

```java
@Transactional
public void checkOut(Long visitId, LocalDateTime at) {
	Visit visit = visits.require(visitId);
	visits.save(visit.checkOut(at));
	events.publish("VisitCompleted", VisitCompleted.of(visit));
}
```

The event is written to the `outbox_event` table in that transaction, so it goes out only if the transaction
commits. A relay then publishes it to SNS, normally within a second. Called outside a transaction, `publish`
throws. The payload is written as JSON: ids and the fields the receivers need, in the formats the catalogue
sets (times with their offset, decimals as strings). Health and care details travel only in the events the
catalogue marks as sensitive, to the services that subscribe to them, and no service logs a payload.

**Handling.** Declare one bean per event type the service handles:

```java
@Component
class VisitCompletedHandler implements EventHandler<VisitCompleted> {

	public String type() { return "VisitCompleted"; }

	public Class<VisitCompleted> payloadType() { return VisitCompleted.class; }

	public void handle(VisitCompleted event, EventMetadata metadata) {
		visits.completed(event.visitId(), event.checkedInAt(), event.checkedOutAt(), event.version());
	}

}
```

`handle` runs in a transaction together with the row in `consumed_message` that marks the event handled.
SQS may deliver a message twice; the second delivery finds the row and is skipped. If `handle` throws, both roll
back, and SQS delivers the event again after the queue's visibility timeout. After five receives the message
goes to the dead-letter queue. An event type the service has no handler for is ignored.

**Settings**, under `carelink.events`:

| Setting | Environment variable | What |
|---|---|---|
| `service` | `CARELINK_EVENTS_SERVICE` | the service's name on its events and in `consumed_message`; defaults to `spring.application.name` |
| `topic-arn` | `CARELINK_EVENTS_TOPICARN` | the topic; the relay runs only when it is set |
| `queue-url` | `CARELINK_EVENTS_QUEUEURL` | the service's queue; the consumer runs only when it is set |
| `endpoint` | `CARELINK_EVENTS_ENDPOINT` | LocalStack, for a local run; not set in the cloud |
| `region` | `CARELINK_EVENTS_REGION` | defaults to `ap-southeast-1` |

Locally, LocalStack in `docker-compose.yml` creates the topic `carelink-events` and the queues
`carelink-core`, `carelink-visit`, `carelink-report` and `carelink-notification`, each with a dead-letter queue
(`scripts/localstack/events.sh`). The compose entry in section 2.4 shows the settings. In the cloud, Terraform
creates the same, plus a filter policy on each subscription, so a queue receives only the types its service
handles.

**The tables.** `outbox_event` and `consumed_message` ship with the library as a platform migration
(`db/platform/V2__events.sql`). Core runs it until the schema split, as it runs every migration.

**Testing.** In unit and slice tests, mock `Events` (`@MockitoBean Events events`) and verify what was
published. A handler is a plain class: call `handle` directly. `EventsIT` in the library runs the whole path
against MySQL and LocalStack.

## 6. The database before the schema split

Until the schema split, the services share core's database and its one schema:

- Core runs every migration. A service sets `spring.flyway.enabled: false` and validates the schema it finds
  (`ddl-auto: validate`).
- A service writes only its own tables ([service-boundaries.md](service-boundaries.md), section 1). It reaches
  core's data through `CoreApi`, not through SQL. The SQL reads that still cross a boundary are listed in
  section 5.2 of that document, and each one is removed before the split.
- A schema change goes into core's migrations. Tell the owner of core before it is merged, so that two changes
  never take the same version number.

At the split, each service gets its own schema and its own Flyway migrations.

## 7. Scheduled jobs

Every service runs at least two replicas, so a scheduled job would run on each of them. Lock every job the way
core does:

- Copy core's `platform/SchedulerLocks.java`, and add the `shedlock-spring` and `shedlock-provider-jdbc-template`
  dependencies.
- Put `@SchedulerLock(name = "<service>.<job>")` on each `@Scheduled` method. Before the split, the
  `shedlock` table core created serves every service; the service name in the lock name keeps them apart.
- Copy `architecture/ScheduledJobLockTest.java`, so that a job without a lock fails the build.

The jobs that still need a lock when they move are `MissedCheckInScheduler` (visit) and `ReportScheduler` and
`ValueAddedSettlementScheduler` (report).

## 8. Before the first pull request

- [ ] `scripts/build.sh <name> test` passes: unit, ArchUnit (copy `architecture/LayerDependencyTest.java`) and
      MySQL integration tests, and at least 80% line coverage in the domain packages.
- [ ] `scripts/run.sh all` starts the service, and `curl localhost:808x/actuator/health/readiness` answers `UP`.
- [ ] Signed in through core in the browser, a page that calls the service works without signing in again.
- [ ] The SonarCloud project `<prefix>_<name>` exists. The repository admin creates it, and the pipeline
      analyses one project per service.
- [ ] The paths the service takes over are listed for the Ingress.

## 9. Still to come

- **The writes and reads across services.** The writes to the `notification` table and the SQL reads of other
  services' tables ([service-boundaries.md](service-boundaries.md), section 5) become events. Each owner moves
  theirs, behind the port the business code already calls.
- **The schema split** comes last, after the reads across schemas are gone.
