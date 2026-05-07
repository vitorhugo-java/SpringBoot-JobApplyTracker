# SpringBoot Job Apply Tracker API

[Frontend - React-JobApplyTracker](https://github.com/vitorhugo-java/React-JobApplyTracker) 

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
| POST | `/api/v1/auth/register` | Register a new user |
| POST | `/api/v1/auth/login` | Login and receive tokens |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| POST | `/api/v1/auth/logout` | Logout and revoke refresh token |
| POST | `/api/v1/auth/forgot-password` | Request password reset |
| POST | `/api/v1/auth/reset-password` | Reset password with token |
| GET | `/api/v1/auth/me` | Get current user info |

### Applications

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/applications` | Create application |
| GET | `/api/v1/applications` | List all (paginated + filterable) |
| GET | `/api/v1/applications/{id}` | Get by ID |
| PUT | `/api/v1/applications/{id}` | Full update |
| PATCH | `/api/v1/applications/{id}/status` | Update status |
| PATCH | `/api/v1/applications/{id}/reminder` | Toggle reminder |
| DELETE | `/api/v1/applications/{id}` | Delete |
| GET | `/api/v1/applications/upcoming` | Upcoming next steps |
| GET | `/api/v1/applications/overdue` | Overdue next steps |

### Gamification

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/gamification/profile` | Get current XP, level, rank title and streak snapshot |
| GET | `/api/v1/gamification/achievements` | List achievement catalog with unlocked state |
| POST | `/api/v1/gamification/events` | Apply a tracked XP event and return updated profile |

### Google Drive

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/google-drive/oauth/start` | Generate the Google OAuth authorization URL for the authenticated user |
| GET | `/api/v1/google-drive/oauth/callback` | Google OAuth callback endpoint used by Google Cloud |
| GET | `/api/v1/google-drive/status` | Return current Google Drive connection status, configured root folder, and base resumes |
| DELETE | `/api/v1/google-drive/connection` | Disconnect the current user's Google account and remove stored Drive preferences |
| PUT | `/api/v1/google-drive/root-folder` | Validate and save the user's base Drive folder |
| POST | `/api/v1/google-drive/base-resumes` | Register a Google Docs base resume by Google Docs URL or file ID |
| DELETE | `/api/v1/google-drive/base-resumes/{baseResumeId}` | Remove a configured base resume |
| POST | `/api/v1/google-drive/applications/{applicationId}/resume-copies` | Copy a configured base resume into the application's Drive subfolder |

## Application Status Values

- `RH`
- `Fiz a RH - Aguardando Atualização`
- `Fiz a Hiring Manager - Aguardando Atualização`
- `Teste Técnico`
- `Fiz teste Técnico - aguardando atualização`
- `RH (Negociação)`

## Gamification System

The backend tracks gamification in `user_gamification`, `achievements`, and `user_achievements`. XP is awarded from application lifecycle events and stored per user, while each application keeps one-time award flags so the same action is not counted twice. The service also derives the current streak and unlocks achievements from the user's non-archived applications.

### XP rules

| Action | Backend event | XP |
|--------|---------------|----|
| New application | `APPLICATION_CREATED` | +10 |
| Recruiter DM sent | `RECRUITER_DM_SENT` | +15 |
| Interview progress | `INTERVIEW_PROGRESS` | +50 |
| Note added | `NOTE_ADDED` | +5 |
| Offer / win | `OFFER_WON` | +500 |

### Level formula

- `level = floor(sqrt(totalXp / 100)) + 1`
- `XP required for level N = 100 * (N - 1)^2`

Examples:

| Level | Total XP required |
|-------|-------------------|
| 1 | 0 |
| 2 | 100 |
| 3 | 400 |
| 4 | 900 |
| 5 | 1600 |

### Rank milestones

| Milestone level | XP threshold | Rank title |
|-----------------|--------------|------------|
| 1 | 0 | Desempregado de Aluguel |
| 6 | 2500 | Job Hunter Iniciante |
| 16 | 22500 | Sobrevivente do LinkedIn |
| 31 | 90000 | Mestre das Soft Skills |
| 51 | 250000 | Lenda das Contratacoes |

### Achievements

| Code | Name | Unlock condition in the backend today |
|------|------|----------------------------------------|
| `EARLY_BIRD` | Early Bird | Have 5 non-archived applications with `applicationDate` set and `createdAt` before `09:00` |
| `NETWORKING_PRO` | Networking Pro | Have 10 recruiter DMs sent inside any rolling 7-day window |
| `PERSISTENT` | Persistent | Reach a 5-day longest streak based on distinct `applicationDate` values |
| `GHOSTBUSTER` | Ghostbuster | Have any non-archived application currently in `GHOSTING` status |

### Current backend status mapping

The backend does not currently have literal `INTERVIEW` or `HIRED` statuses.

- `INTERVIEW_PROGRESS` is awarded when `interviewScheduled = true` or when the application enters one of these statuses: `Fiz a RH - Aguardando Atualização`, `Fiz a Hiring Manager - Aguardando Atualização`, `Teste Técnico`, `Fiz teste Técnico - aguardando atualização`, or `RH (Negociação)`.
- `OFFER_WON` is currently mapped to `RH (Negociação)` (`RH_NEGOCIACAO` in code), which is the backend's current closing-stage proxy until dedicated offer/hired statuses exist.
- `GHOSTBUSTER` currently unlocks from `GHOSTING` status itself; although the seeded achievement description mentions "30 days", the implemented unlock rule is status-based today.

## Running Locally

### With Docker Compose

Development (build locally):

```bash
docker-compose up -d
```

Production (use pre-built image from GitHub Container Registry):

1. Log in to GHCR (requires a Personal Access Token with `read:packages`):

```bash
# POSIX / macOS / WSL
echo $CR_PAT | docker login ghcr.io -u vitorhugo-java --password-stdin

# Windows PowerShell
# $env:CR_PAT | docker login ghcr.io -u vitorhugo-java --password-stdin
```

2. Pull the production compose file image(s):

```bash
docker compose -f docker-compose.prod.yml pull
```

3. Start the services from the production compose file:

```bash
docker compose -f docker-compose.prod.yml up -d
```

By default this compose file pulls image: `ghcr.io/vitorhugo-java/springboot-jobapplytracker:latest`. Change the image name in `docker-compose.prod.yml` if you publish with a different tag or repository.

The API will be available at `http://localhost:8080`.

### With Maven (requires a running MariaDB)

```bash
export DB_URL=jdbc:mariadb://localhost:3306/jobtracker?createDatabaseIfNotExist=true
export DB_USERNAME=jobtracker
export DB_PASSWORD=jobtracker
export JWT_SECRET=your-secret-key-at-least-256-bits-long
export GOOGLE_DRIVE_CLIENT_ID=your-google-client-id
export GOOGLE_DRIVE_CLIENT_SECRET=your-google-client-secret
export GOOGLE_DRIVE_REDIRECT_URI=http://localhost:8080/api/v1/google-drive/oauth/callback
export GOOGLE_DRIVE_OAUTH_COMPLETE_URL=http://localhost:5173/settings/google-drive/callback
mvn spring-boot:run
```

## Google Drive integration

This backend supports per-user Google Drive OAuth2 for resume-copy automation. It does **not** replace the app's JWT auth flow; users stay authenticated with the existing bearer token, and Google is connected separately with `POST /api/v1/google-drive/oauth/start`.

### Required Google Cloud setup

1. Create a Google Cloud OAuth client for a web application.
2. Enable the **Google Drive API**.
3. Add the backend callback URL as an authorized redirect URI. Example local value:
   - `http://localhost:8080/api/v1/google-drive/oauth/callback`
4. Configure these environment variables:

| Variable | Required | Description |
|----------|----------|-------------|
| `GOOGLE_DRIVE_CLIENT_ID` | Yes | Google OAuth client ID |
| `GOOGLE_DRIVE_CLIENT_SECRET` | Yes | Google OAuth client secret |
| `GOOGLE_DRIVE_REDIRECT_URI` | Yes | Backend callback URL registered in Google Cloud |
| `GOOGLE_DRIVE_OAUTH_COMPLETE_URL` | Yes | Frontend page that receives the final `status` and `message` query params after OAuth finishes |
| `GOOGLE_DRIVE_AUTHORIZATION_URI` | No | Override Google authorization endpoint |
| `GOOGLE_DRIVE_TOKEN_URI` | No | Override Google token endpoint |

### OAuth flow expectations

1. Frontend calls `POST /api/v1/google-drive/oauth/start` with the user's JWT bearer token.
2. Backend creates a short-lived OAuth state tied to that authenticated user and returns:
   - `authorizationUrl`
   - `state`
   - `redirectUri`
   - `scopes`
3. Frontend opens `authorizationUrl` in a new tab or popup.
4. Google redirects back to `GET /api/v1/google-drive/oauth/callback`.
5. Backend exchanges the authorization code for user-scoped Drive credentials, stores them, and redirects the browser to `GOOGLE_DRIVE_OAUTH_COMPLETE_URL` with:
   - `status=success|error`
   - `message=<url-encoded message>`

### Scope used

- `https://www.googleapis.com/auth/drive`

This scope is used so the backend can validate user-selected Drive folders, read chosen Google Docs metadata, create vacancy subfolders, and copy Google Docs files on behalf of the authenticated user.

### Supported files

- Base resumes must be **Google Docs** (`application/vnd.google-apps.document`).
- The root folder must be a **Google Drive folder**.
- The frontend Gemini button that opens `https://gemini.google.com/gem/f8ed7c14b062` is frontend-only and does not require a backend endpoint.

### Resume copy behavior

When the frontend later calls `POST /api/v1/google-drive/applications/{applicationId}/resume-copies`:

1. Backend verifies the current user owns the application.
2. Backend refreshes the user's Google access token if needed.
3. Backend verifies the configured root folder still exists and is a folder.
4. Backend finds or creates a vacancy subfolder under that root folder using the application identity.
5. Backend copies the selected base Google Doc into that subfolder.
6. Backend renames the copy with an `APP-<application-uuid>` prefix plus vacancy context.
7. Backend returns a Google Docs web URL for the copied file.

### Google Drive request/response shapes

`POST /api/v1/google-drive/oauth/start`

```json
{}
```

Response:

```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "state": "generated-state",
  "redirectUri": "http://localhost:8080/api/v1/google-drive/oauth/callback",
  "scopes": [
    "https://www.googleapis.com/auth/drive"
  ]
}
```

`GET /api/v1/google-drive/status`

```json
{
  "configured": true,
  "connected": true,
  "googleEmail": "user@gmail.com",
  "googleDisplayName": "User Name",
  "googleAccountId": "permission-id",
  "rootFolderId": "drive-folder-id",
  "rootFolderName": "Job Tracker Root",
  "connectedAt": "2026-05-05T12:00:00",
  "baseResumes": [
    {
      "id": "uuid",
      "googleFileId": "google-doc-id",
      "documentName": "Resume Base",
      "webViewLink": "https://docs.google.com/document/d/google-doc-id/edit",
      "createdAt": "2026-05-05T12:05:00"
    }
  ]
}
```

`PUT /api/v1/google-drive/root-folder`

```json
{
  "folderIdOrUrl": "https://drive.google.com/drive/folders/drive-folder-id"
}
```

`POST /api/v1/google-drive/base-resumes`

```json
{
  "documentIdOrUrl": "https://docs.google.com/document/d/google-doc-id/edit"
}
```

`POST /api/v1/google-drive/applications/{applicationId}/resume-copies`

```json
{
  "baseResumeId": "base-resume-uuid"
}
```

Response:

```json
{
  "applicationId": "application-uuid",
  "baseResumeId": "base-resume-uuid",
  "copiedFileId": "copied-google-doc-id",
  "copiedFileName": "APP-application-uuid - Backend Engineer - Resume Base",
  "documentWebViewLink": "https://docs.google.com/document/d/copied-google-doc-id/edit",
  "vacancyFolderId": "vacancy-folder-id",
  "vacancyFolderName": "Backend Engineer - APP-application-uuid",
  "vacancyFolderWebViewLink": "https://drive.google.com/drive/folders/vacancy-folder-id"
}
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
| `GOOGLE_DRIVE_CLIENT_ID` | *(empty)* | Google OAuth client ID for Drive integration |
| `GOOGLE_DRIVE_CLIENT_SECRET` | *(empty)* | Google OAuth client secret for Drive integration |
| `GOOGLE_DRIVE_REDIRECT_URI` | `http://localhost:8080/api/v1/google-drive/oauth/callback` | OAuth callback URL registered in Google Cloud |
| `GOOGLE_DRIVE_OAUTH_COMPLETE_URL` | *(empty)* | Frontend URL that receives OAuth completion redirects |
| `RATE_LIMIT_AUTH_LOGIN_LIMIT_FOR_PERIOD` | `10` | Max login requests allowed per refresh period |
| `RATE_LIMIT_AUTH_LOGIN_REFRESH_PERIOD` | `1m` | Window used by the login rate limiter |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP gRPC endpoint (Jaeger/OpenTelemetry collector) |
| `PROMETHEUS_URL` | `http://localhost:9090` | Prometheus base URL for observability integrations |
| `SERVER_PORT` | `8080` | Server port |

## Monitoring

Spring Boot Actuator runs on a dedicated management port **8081**, completely separate from the main API port (8080). This port is **never exposed to the host machine** in Docker Compose — it is only reachable within the internal `infra_network` Docker network.

Prometheus scrapes metrics directly from the container over the internal network:

```yaml
scrape_configs:
  - job_name: job-tracker
    static_configs:
      - targets: ['app:8081']
    metrics_path: /actuator/prometheus
    scheme: http
    scrape_interval: 15s
```

No authentication token is required — network-level isolation (Docker bridge network) is the security boundary. The Actuator is unreachable from outside the Docker network.

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
