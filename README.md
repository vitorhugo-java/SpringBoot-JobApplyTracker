# SpringBoot Job Apply Tracker API

[Frontend (este diretório)](./) | [Backend Spring Boot](../SpringBoot-JobApplyTracker/) | [README Backend](../SpringBoot-JobApplyTracker/README.md)

A production-ready Spring Boot REST API for tracking job applications, built with Java 21, Spring Security JWT authentication, MariaDB, and comprehensive test coverage.

## Tech Stack

- **Java 21**
- **Spring Boot 3.2** (Web, Data JPA, Security, Validation)
- **Spring Security** with stateless JWT authentication
- **JWT + Refresh Tokens** (access: 15 min, refresh: 7 days with rotation)
- **Resilience4j Rate Limiting** on auth endpoints
- **MariaDB** (production) / **Testcontainers** (tests)
- **Flyway** for DB migrations
- **JUnit 5 + Mockito** (unit tests)
- **Testcontainers + MockMvc** (integration tests)
- **RestAssured** (E2E tests)
- **Maven**

## Project Structure

```
.
├── src/
│   ├── main/java/com/jobtracker/
│   │   ├── config/          # Security, JWT, CORS, filters
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Request/Response DTOs
│   │   ├── entity/          # JPA entities
│   │   ├── exception/       # Global exception handling
│   │   ├── mapper/          # Entity-DTO mappers
│   │   ├── repository/      # Spring Data JPA repositories
│   │   ├── service/         # Business logic
│   │   └── util/            # Utilities
│   ├── main/resources/
│   │   ├── application.yml
│   │   └── db/migration/    # Flyway migrations
│   └── test/java/com/jobtracker/
│       ├── unit/            # Mockito unit tests
│       ├── integration/     # SpringBootTest + Testcontainers + MockMvc
│       └── e2e/             # RestAssured end-to-end tests
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## API Endpoints

### Auth

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive tokens |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/logout` | Logout and revoke refresh token |
| POST | `/api/auth/forgot-password` | Request password reset |
| POST | `/api/auth/reset-password` | Reset password with token |
| GET | `/api/auth/me` | Get current user info |

### Applications

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/applications` | Create application |
| GET | `/api/applications` | List all (paginated + filterable) |
| GET | `/api/applications/{id}` | Get by ID |
| PUT | `/api/applications/{id}` | Full update |
| PATCH | `/api/applications/{id}/status` | Update status |
| PATCH | `/api/applications/{id}/reminder` | Toggle reminder |
| DELETE | `/api/applications/{id}` | Delete |
| GET | `/api/applications/upcoming` | Upcoming next steps |
| GET | `/api/applications/overdue` | Overdue next steps |

## Application Status Values

- `RH`
- `Fiz a RH - Aguardando Atualização`
- `Fiz a Hiring Manager - Aguardando Atualização`
- `Teste Técnico`
- `Fiz teste Técnico - aguardando atualização`
- `RH (Negociação)`

## Running Locally

### With Docker Compose

```bash
docker-compose up -d
```

The API will be available at `http://localhost:8080`.

### With Maven (requires a running MariaDB)

```bash
export DB_URL=jdbc:mariadb://localhost:3306/jobtracker?createDatabaseIfNotExist=true
export DB_USERNAME=jobtracker
export DB_PASSWORD=jobtracker
export JWT_SECRET=your-secret-key-at-least-256-bits-long
mvn spring-boot:run
```

## Running Tests

```bash
# All tests
mvn verify

# Unit tests only
mvn test -Dtest="com.jobtracker.unit.*"

# Integration tests only
mvn test -Dtest="com.jobtracker.integration.*"

# E2E tests only
mvn test -Dtest="com.jobtracker.e2e.*"
```

> **Note:** Integration and E2E tests require Docker to be running (Testcontainers pulls a MariaDB image automatically).

## Seed Fake Data

This project includes a startup seeder that can generate fake job applications using the Java library `net.datafaker:datafaker`.

The seeder is disabled by default and only runs when explicitly enabled.

Required parameters:

- `APP_SEED_ENABLED=true`
- `APP_SEED_USER_EMAIL=<existing user email>`

Optional:

- `APP_SEED_COUNT=1000` (default is `1000`)

Example with Maven:

```bash
export APP_SEED_ENABLED=true
export APP_SEED_USER_EMAIL=user@example.com
export APP_SEED_COUNT=1000
mvn spring-boot:run
```

Example with `java -jar`:

```bash
APP_SEED_ENABLED=true APP_SEED_USER_EMAIL=user@example.com APP_SEED_COUNT=1500 java -jar target/job-tracker-1.0.0.jar
```

If `APP_SEED_ENABLED=true` and `APP_SEED_USER_EMAIL` is not provided (or the user does not exist), the application startup fails with a clear error.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:mariadb://localhost:3306/jobtracker` | JDBC URL |
| `DB_USERNAME` | `jobtracker` | DB username |
| `DB_PASSWORD` | `jobtracker` | DB password |
| `JWT_SECRET` | *(dev default)* | JWT signing secret (min 256 bits) |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | `900000` | Access token TTL (15 min) |
| `JWT_REFRESH_TOKEN_EXPIRATION_MS` | `604800000` | Refresh token TTL (7 days) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Allowed CORS origins |
| `RATE_LIMIT_AUTH_LOGIN_LIMIT_FOR_PERIOD` | `10` | Max login requests allowed per refresh period |
| `RATE_LIMIT_AUTH_LOGIN_REFRESH_PERIOD` | `1m` | Window used by the login rate limiter |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP gRPC endpoint (Jaeger/OpenTelemetry collector) |
| `PROMETHEUS_URL` | `http://localhost:9090` | Prometheus base URL for observability integrations |
| `SERVER_PORT` | `8080` | Server port |

## Rate Limiting

Auth endpoints are protected with Resilience4j rate limiters. When a limit is exceeded, the API returns `429 Too Many Requests` with the standard error payload used by the application.

## CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`) triggers on push/PR to `main`:

1. Checkout
2. Setup Java 21
3. Build project
4. Run unit tests
5. Run integration tests (Testcontainers)
6. Run E2E tests (Testcontainers + RestAssured)
7. Full `mvn verify`

## API Documentation

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the app is running.
