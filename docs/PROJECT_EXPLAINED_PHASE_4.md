# Project Explained — Phase 4: Docker, Swagger & Documentation

Every file created or finalized in Phase 4 is documented below.

---

## Config Package

---

#### OpenApiConfig

**Package:** `com.securebank.config`
**Type:** Class (`@Configuration`)
**Purpose:** Configures the Springdoc OpenAPI bean that drives Swagger UI — sets the API title,
version, and JWT bearer authentication so that Swagger UI's **Authorize** button works correctly.

**Why this class exists (SOLID principle):**
Single Responsibility — all OpenAPI/Swagger configuration is in one place. Controllers stay free
of Swagger annotations; the configuration is applied globally from here.
Open/Closed — adding a new API version or contact info requires only editing this class, not
touching any controller.

**Methods:**

- **`openAPI() → OpenAPI`** *(bean)*
  - What it does: Builds and returns a configured `OpenAPI` object that Springdoc uses to generate
    the `/v3/api-docs` JSON endpoint.
  - `new Info(...)` — sets the title, description, and version shown at the top of Swagger UI.
  - `addSecurityItem(new SecurityRequirement().addList("bearerAuth"))` — applies the JWT security
    requirement globally. Every endpoint in Swagger UI shows the padlock icon indicating
    authentication is required.
  - `Components.addSecuritySchemes("bearerAuth", new SecurityScheme(...))` — defines the scheme
    named `"bearerAuth"` as an HTTP Bearer token (JWT format). This wires the **Authorize** button
    in Swagger UI — when a developer pastes their JWT token there, all subsequent Swagger test
    requests include `Authorization: Bearer <token>` automatically.
  - Annotation: `@Bean` — Spring calls this method once at startup and stores the `OpenAPI` instance;
    Springdoc finds it and uses it to generate documentation.

**How it connects to other classes:**
- Springdoc scans all `@RestController` classes (`AuthController`, `AccountController`,
  `TransactionController`) automatically — no annotation changes needed on those classes.
- The generated spec is served at `/v3/api-docs` and rendered by Swagger UI at `/swagger-ui.html`.
- The `bearerAuth` scheme name must match what is used in `addSecurityItem()` — a typo here would
  show the **Authorize** button but not actually pass the token.

---

## Infrastructure Files

---

#### Dockerfile (finalized)

**Purpose:** Defines the two-stage build process that produces a lean, production-ready Docker image
for the SecureBank API.

**Why this file exists:**
Without it, deploying the application requires the target machine to have Java, Maven, and the right
versions of both installed. The `Dockerfile` makes the build and runtime environment fully
self-contained and reproducible — `docker compose up --build` is the only command needed.

**Stage-by-stage breakdown:**

**Stage 1 — `builder`:**
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
```
- Starts from an official Maven image that already includes JDK 17 and Maven 3.9. No installation
  step needed — reduces build time and avoids version drift.

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -q
```
- Copies only `pom.xml` before copying source code. `mvn dependency:go-offline` downloads all
  declared Maven dependencies into the local cache. Docker stores this as a separate layer.
- **Why this order matters:** If you change `src/` files but not `pom.xml`, Docker reuses this
  cached dependency layer — the ~30 second download is skipped on every subsequent rebuild.

```dockerfile
COPY src ./src
RUN mvn package -DskipTests -q
```
- Copies source code (changes often) after the dependency layer (changes rarely).
- Compiles, runs annotation processors, and packages everything into a fat JAR. `-DskipTests` avoids
  running tests inside the container build — tests run in CI separately.

**Stage 2 — runtime:**
```dockerfile
FROM eclipse-temurin:17-jre-alpine
```
- Switches to a lightweight Alpine-based JRE image. Does not include Maven, JDK tools (`javac`,
  `javap`), or source code from Stage 1 — only the JRE needed to run the jar.
- Result: the final image is approximately 180 MB instead of 500+ MB.

```dockerfile
COPY --from=builder /app/target/securebank-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
- `COPY --from=builder` selectively copies only the jar from Stage 1 — none of the build tools or
  intermediate class files are included.
- `EXPOSE 8080` documents the port the application listens on (does not publish it — that is
  `docker-compose.yml`'s job with `ports:`).
- `ENTRYPOINT` sets the command that runs when the container starts. The exec form `["java", "-jar",
  "app.jar"]` is preferred over shell form — it runs Java directly as PID 1, so `SIGTERM` from
  Docker is received by the JVM for graceful shutdown.

---

#### docker-compose.yml (finalized)

**Purpose:** Defines the full local development and production-equivalent environment — PostgreSQL
database and Spring Boot API — as two coordinated services with networking, health checking, and
data persistence.

**Why this file exists:**
Orchestrates multi-container startup so a developer needs only one command:
`docker compose up --build`.

**Service: `db` (PostgreSQL)**

```yaml
image: postgres:15-alpine
```
- Uses the official lightweight Alpine variant of PostgreSQL 15. Alpine images are typically
  40–60 MB vs 200+ MB for the Debian-based default.

```yaml
environment:
  POSTGRES_DB: securebank
  POSTGRES_USER: securebank
  POSTGRES_PASSWORD: securebank
```
- PostgreSQL reads these environment variables on first startup to create the database and user.
  The application connects with these same credentials.

```yaml
volumes:
  - postgres_data:/var/lib/postgresql/data
```
- Maps the named volume `postgres_data` to PostgreSQL's data directory. All tables, rows, and
  indexes persist here. Running `docker compose down` stops the containers but keeps this volume.
  Running `docker compose down -v` also removes it (wipes the database).

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U securebank"]
  interval: 10s
  timeout: 5s
  retries: 5
```
- `pg_isready` is PostgreSQL's built-in connectivity check tool. Docker runs it every 10 seconds.
  After 5 consecutive successes, the service is marked `healthy`. The `api` service waits for this
  before starting.

**Service: `api` (Spring Boot)**

```yaml
build: .
```
- Instructs Compose to build the image using the `Dockerfile` in the current directory. On the
  first run (or after `--build`), Docker runs the multi-stage build. Subsequent runs use the
  cached image.

```yaml
restart: unless-stopped
```
- If the API container crashes (e.g., an uncaught exception that kills the JVM), Docker automatically
  restarts it. `unless-stopped` means it does not restart if explicitly stopped with `docker stop`.

```yaml
depends_on:
  db:
    condition: service_healthy
```
- Stronger than the default `service_started`. Compose will not create the API container until
  PostgreSQL passes its healthcheck. Without this, Spring Boot would attempt `DataSource`
  initialisation against a database that is still starting up and fail with a connection error.

```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/securebank
  JWT_SECRET: ${JWT_SECRET}
```
- Inside Docker's network, services communicate by service name — `db` resolves to the PostgreSQL
  container's IP address automatically. `localhost:5432` would not work here.
- `${JWT_SECRET}` is interpolated from the host machine's shell environment at compose startup.
  If the variable is not set, Compose raises an error before any container starts.

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://localhost:8080/v3/api-docs > /dev/null || exit 1"]
  start_period: 40s
```
- Polls the OpenAPI spec endpoint every 30 seconds after a 40-second grace period (Spring Boot
  startup takes 15–25 seconds on first run). Other services or orchestrators (Kubernetes, AWS ECS)
  can use this healthcheck to determine when the API is ready to receive traffic.

**Named volume: `postgres_data`**
```yaml
volumes:
  postgres_data:
```
- Declaring the volume at the top level registers it with Docker. Docker manages the physical storage
  location (typically `/var/lib/docker/volumes/` on Linux). The volume outlives the containers
  and survives `docker compose down`.

---

## Documentation Files

---

#### README.md

**Purpose:** The human-facing entry point for anyone who clones the repository. Covers what the
project does, how to run it, the full API reference, sample `curl` commands, testing instructions,
design decisions, and future improvement ideas.

**Created in:** Phase 1 (all content matching the Phase 4 specification was written upfront).

**Sections:**
| Section | What it covers |
|---|---|
| Overview | What SecureBank does and why it was built |
| Architecture | ASCII layer diagram from Client to PostgreSQL |
| SOLID Principles Applied | One paragraph per principle with class name examples |
| Tech Stack | Table with version and rationale for each technology |
| Getting Started | Docker Compose and local Maven setup instructions |
| API Reference | All 10 endpoints — method, path, auth, description |
| Sample Requests | `curl` examples for register, login, account creation, deposit, transfer |
| Testing | `mvn test` commands, coverage table by layer |
| Design Decisions | `@Transactional`, constructor injection, interfaces, JWT vs sessions |
| Future Improvements | Kafka, rate limiting, audit log, refresh tokens |

---

#### docs/CONCEPTS_PHASE_1.md through CONCEPTS_PHASE_4.md

**Purpose:** Beginner-friendly explanations of every technical concept introduced per phase.
Each concept follows the same structure: what it is, why we use it, how it works in this project
(with a code snippet), and a real-world analogy.

| File | Concepts covered |
|---|---|
| `CONCEPTS_PHASE_1.md` | Enums, JPA entities, `@Id`, `@GeneratedValue`, relationships, repositories, DTOs, `@Valid`, custom exceptions, `@ControllerAdvice`, Maven, `application.properties`, `BigDecimal`, project structure |
| `CONCEPTS_PHASE_2.md` | Security filter chain, JWT structure, BCrypt, `@Bean`/`@Configuration`, `UserDetails`/`UserDetailsService`, `OncePerRequestFilter`, 401 vs 403, stateless auth, constructor injection, `@Service` |
| `CONCEPTS_PHASE_3.md` | `@Transactional`, `@RestController`, `@RequestMapping`, `@PathVariable` vs `@RequestParam`, `ResponseEntity`, service layer pattern, Mockito mocks, `@SpringBootTest` vs `MockitoExtension`, MockMvc, pagination |
| `CONCEPTS_PHASE_4.md` | Image vs container, Dockerfile instructions, multi-stage builds, Docker Compose services and `depends_on`, environment variables, OpenAPI/Swagger UI, why we document APIs, volumes and ports |

---

#### docs/PROJECT_EXPLAINED_PHASE_1.md through PROJECT_EXPLAINED_PHASE_4.md

**Purpose:** Per-class reference documentation for every class and interface in the project.
Each entry follows the same structure: package, type, one-sentence purpose, which SOLID principle
it demonstrates, a breakdown of every method, and how it connects to other classes.

| File | Classes documented |
|---|---|
| `PROJECT_EXPLAINED_PHASE_1.md` | `SecureBankApplication`, `User`, `Account`, `Transaction`, all 4 enums, all 3 repositories, all 9 DTOs, all 3 exceptions + `GlobalExceptionHandler`, all 3 service interfaces |
| `PROJECT_EXPLAINED_PHASE_2.md` | `JwtUtil`, `JwtFilter`, `UserDetailsServiceImpl`, `SecurityConfig`, `AuthServiceImpl`, `AuthController`, `GlobalExceptionHandler` (update), `AuthControllerTest` |
| `PROJECT_EXPLAINED_PHASE_3.md` | `AccountServiceImpl`, `TransactionServiceImpl`, `AccountController`, `TransactionController`, `AccountServiceTest`, `TransactionServiceTest`, `AccountControllerTest`, `TransactionControllerTest` |
| `PROJECT_EXPLAINED_PHASE_4.md` | `OpenApiConfig`, `Dockerfile` (finalized), `docker-compose.yml` (finalized), `README.md`, all concept and explained doc files |