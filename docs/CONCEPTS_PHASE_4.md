# Concepts — Phase 4: Docker, Swagger & Documentation

---

## Docker Image vs Container

**What is it?**
A Docker **image** is a read-only blueprint — a snapshot of a filesystem with an application, its
dependencies, and instructions on how to run it. A **container** is a running instance of that image.
You can run many containers from the same image, each isolated from the others.

**Why do we use it?**
Without Docker, "it works on my machine" is a real problem — different developers have different Java
versions, different PostgreSQL versions, different OS configurations. A Docker image captures
everything the application needs so it runs identically on any machine: your laptop, CI, production.

**How it works in this project:**
```bash
# Build the image from the Dockerfile (the blueprint)
docker build -t securebank-api .

# Run a container from that image (the live instance)
docker run -p 8080:8080 securebank-api

# docker compose does both for all services at once
docker compose up --build
```
The `docker compose up --build` command builds the `securebank-api` image from the `Dockerfile`,
then creates and starts two containers: one for PostgreSQL and one for the Spring Boot API.

**Analogy:**
A Docker image is like a cake recipe and a pre-measured ingredient kit. A container is the actual
cake that results when you follow that recipe. You can bake the same cake in any kitchen anywhere
in the world and get exactly the same result.

---

## Dockerfile Instructions: `FROM`, `COPY`, `RUN`, `ENTRYPOINT`

**What is it?**
A `Dockerfile` is a text file with a sequence of instructions. Each instruction adds a layer to
the image. Docker caches each layer — if a layer has not changed since the last build, Docker reuses
the cached version and skips re-running it.

**Why do we use it?**
The order of instructions directly affects build speed. Instructions that change rarely (like
downloading dependencies) should come before instructions that change often (like copying source
code). This way, rebuilding after a code change skips the slow dependency download step.

**How it works in this project:**
```dockerfile
# Stage 1: build
FROM maven:3.9-eclipse-temurin-17 AS builder   # base image with Java 17 + Maven pre-installed
WORKDIR /app

COPY pom.xml .                                  # copy ONLY pom.xml first
RUN mvn dependency:go-offline -q                # download all deps → cached as one layer

COPY src ./src                                  # copy source code (changes often)
RUN mvn package -DskipTests -q                  # compile and package → produces the .jar

# Stage 2: runtime (much smaller — no Maven, no JDK, just JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar   # only copy the final jar
EXPOSE 8080                                     # document the port (does not publish it)
ENTRYPOINT ["java", "-jar", "app.jar"]          # command to run when the container starts
```

**Why two `FROM` statements (multi-stage build)?**
The builder stage needs the full JDK and Maven (heavy). The runtime stage only needs the JRE
(lightweight). Without multi-stage, the final image would ship Maven and the JDK — hundreds of MB
the running application does not need. The final image is typically under 200 MB.

**The caching trick explained:**
```
pom.xml copied → dependencies downloaded (SLOW, ~30s)  ← cached after first build
src/ copied    → source compiled (FAST, ~5s)
```
On every subsequent build where only source code changes, Docker reuses the dependency layer and
only re-runs the compile step.

**Analogy:**
`FROM` is choosing which kitchen to cook in (base image). `COPY` is bringing ingredients. `RUN`
is doing the cooking. `ENTRYPOINT` is the instruction on the box: "to serve, heat for 30 minutes."
Multi-stage is like cooking in a professional kitchen and then plating only the finished dish —
you don't ship the commercial oven to the customer.

---

## Docker Compose Services and `depends_on`

**What is it?**
Docker Compose is a tool for defining and running multi-container applications. A `docker-compose.yml`
file describes all the services (containers), their configuration, and how they relate to each other.
`depends_on` tells Compose which services must start before another.

**Why do we use it?**
The Spring Boot API needs the PostgreSQL database to be ready before it can connect. Without
`depends_on`, Compose might start the API before the database is listening, causing a connection
error on startup.

**How it works in this project:**
```yaml
services:
  db:
    image: postgres:15-alpine
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U securebank"]
      interval: 10s

  api:
    build: .
    depends_on:
      db:
        condition: service_healthy    # wait until db passes its healthcheck
```
`condition: service_healthy` is stronger than the default `condition: service_started`. It waits
until PostgreSQL's `pg_isready` command returns success — meaning the database is fully initialised
and accepting connections, not just that the container process has started.

**Analogy:**
`depends_on` is like a film crew's call sheet. The director (API) cannot start shooting until the
set (database) is fully built and confirmed ready — not just when the construction crew (container
process) has arrived on site.

---

## Environment Variables in Docker

**What is it?**
Environment variables are key-value pairs injected into a container at runtime from outside the
image. They configure the application without embedding secrets or environment-specific values into
the image itself.

**Why do we use it?**
The database password and JWT secret must never be hard-coded in source files or baked into Docker
images — those get pushed to Git or Docker Hub where anyone can see them. Environment variables
are injected at deploy time from a secure external source.

**How it works in this project:**
```yaml
# docker-compose.yml
api:
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/securebank
    JWT_SECRET: ${JWT_SECRET}    # reads from the host shell environment
```

```properties
# application.properties — Spring Boot reads the env var
jwt.secret=${JWT_SECRET:change-me-in-production}
```

```bash
# Usage: export the secret in your shell before running Compose
export JWT_SECRET="my-very-long-production-secret-key-256-bits"
docker compose up --build
```

`${JWT_SECRET}` in `docker-compose.yml` interpolates the value from the host machine's shell
environment. Spring Boot's `${JWT_SECRET:fallback}` syntax reads the OS environment variable at
startup and falls back to `"fallback"` if it is not set.

**Analogy:**
Environment variables are like a combination safe. The safe (container) has a slot for a code (the
secret). Different environments use different codes — the safe design (image) never contains the
code written inside it.

---

## OpenAPI and Swagger UI

**What is it?**
OpenAPI (formerly Swagger) is a standard specification for describing REST APIs in a machine-readable
format (JSON or YAML). Swagger UI is a web interface that reads that specification and renders an
interactive API browser where developers can read documentation and make live test requests.

**Why do we use it?**
Without API documentation, every developer integrating with SecureBank must read the source code to
understand what endpoints exist, what they accept, and what they return. Swagger UI generates
documentation automatically from the code — it is always up to date and lets developers test
endpoints directly in the browser without Postman or curl.

**How it works in this project:**
```java
// OpenApiConfig.java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
            .info(new Info().title("SecureBank API").version("1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")));
}
```
`springdoc-openapi` scans all `@RestController` classes at startup and generates `/v3/api-docs`
(the machine-readable JSON). Swagger UI reads that JSON and renders it at `/swagger-ui.html`.

The `SecurityScheme` adds an **Authorize** button to Swagger UI. Paste a JWT token there and every
subsequent test request from the browser will include `Authorization: Bearer <token>` automatically.

**Flow to test in browser:**
1. `POST /api/auth/register` → copy the token
2. Click **Authorize** → paste the token
3. Try `POST /api/transactions/deposit` — the token is included automatically

**Analogy:**
OpenAPI is like a restaurant menu printed from the actual kitchen's recipe system — it can never be
out of date because it is generated from the real recipes. Swagger UI is the physical menu the
waiter hands you, where you can also place your order directly.

---

## Why We Document APIs

**What is it?**
API documentation is a description of every endpoint, its inputs, its outputs, its error responses,
and its authentication requirements — written so that any developer can integrate with the API
without reading the implementation code.

**Why do we use it?**
An undocumented API is unusable to external developers, future teammates, and even your future self.
Good documentation:
- Reduces the time to integrate from hours to minutes
- Prevents misuse (wrong content types, missing headers, invalid payloads)
- Serves as a contract — clients know what to expect and when the API changes

In a professional environment, the API documentation is often the first thing a new team member or
integration partner reads.

**How it works in this project:**
Three layers of documentation exist:
1. **`README.md`** — human-readable overview, architecture, setup, sample `curl` commands
2. **Swagger UI at `/swagger-ui.html`** — interactive, always up-to-date, generated from code
3. **`docs/PROJECT_EXPLAINED_PHASE_*.md`** — learning documentation explaining every class

**Analogy:**
Documenting an API is like writing an instruction manual for a piece of equipment. The machine works
without the manual — but only the engineer who built it can use it. The manual makes it accessible
to everyone.

---

## Volumes and Ports in Docker Compose

**What is it?**
**Volumes** persist data outside the container's filesystem so it survives container restarts and
re-creations. **Ports** map a port on the host machine to a port inside the container, making the
service accessible from outside Docker.

**Why do we use it?**
Without volumes, all PostgreSQL data is stored inside the container's writable layer — when you run
`docker compose down`, the data is gone. A named volume persists on the host. Without port mapping,
the API container listens on port 8080 inside the Docker network but nothing outside Docker can
reach it.

**How it works in this project:**
```yaml
services:
  db:
    ports:
      - "5432:5432"       # host:container — exposes Postgres to host for debugging
    volumes:
      - postgres_data:/var/lib/postgresql/data   # named volume → data survives restarts

  api:
    ports:
      - "8080:8080"       # host:container — http://localhost:8080 reaches the Spring Boot app

volumes:
  postgres_data:           # declares the named volume (Docker manages the storage location)
```

Port mapping format is `"HOST_PORT:CONTAINER_PORT"`. The left side is what you type in your browser
(`localhost:8080`). The right side is what the process inside the container listens on (`8080`).

In production you would typically **not** expose the database port (5432) to the host — it is
exposed here for development convenience (to connect with DBeaver or pgAdmin).

**Analogy:**
Volumes are like an external hard drive plugged into a computer. Even if the computer (container)
is replaced, the hard drive (volume) keeps all the files. Port mapping is like a phone extension:
calling extension `8080` from outside the building (host) rings the phone at desk `8080` inside
(container).