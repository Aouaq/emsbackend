# ElectricWave Access Backend

Kotlin/Spring Boot backend for the ElectricWave Flutter application. It provides
email/password and native Google authentication, and account administration. It does
not serve feed data: clients read Emoncms directly with the key they are entitled to.

The backend never generates claim codes. A claim code is the Emoncms read key
printed in the QR code or embedded in the Flutter deep link. Registration does
not accept a key; a client links an account later with `POST /api/me/keys`.

## Requirements

- JDK 17
- Docker (PostgreSQL 16 and Redis 7, or Testcontainers for tests)
- Windows: use `.\gradlew.bat`; Linux/macOS: use `./gradlew`

## Quick start (development)

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
docker compose up -d
.\gradlew.bat bootRun
```

The default profile is `dev` and listens on `http://localhost:8082`. The
bootstrap administrator is `admin@electricwave.local` / `admin123`. In `dev`,
verification and reset codes are returned in API responses when mail is disabled.

The development database must be reset if it still contains Flyway history for
the previous `V1__core_schema.sql`:

```powershell
docker compose down -v
docker compose up -d
```

Swagger UI is available to admins at `http://localhost:8082/swagger-ui.html`.
The OpenAPI document is at `/v3/api-docs`. The server-rendered admin UI is at
`/admin/dashboard`; `/admin/alarms` shows the fleet-wide alarm list and lets an
administrator tune per-site limits.

## Configuration

All configuration is environment-driven. Do not deploy the development defaults.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` (default) or `prod`. The Docker image pins `prod`; startup aborts outside `dev` if any development secret is still set |
| `APP_JWT_SECRET` | HS256 secret, at least 32 bytes |
| `APP_CRYPTO_MASTER_KEY` | Base64 32-byte AES-256-GCM key |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | Bootstrap admin |
| `APP_OAUTH2_GOOGLE_CLIENT_ID` | Google ID-token audience |
| `APP_MAIL_ENABLED`, `APP_MAIL_FROM` | Whether to send at all, and the `From` address |
| `APP_MAIL_TRANSPORT` | `smtp` (default) or `brevo`. Use `brevo` where outbound SMTP is blocked — Render drops 25/465/587, so a JavaMail connect there hangs for the full timeout and then fails on every send |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_LOCALHOST` | `transport=smtp` only. Port 2525 is an alternative submission port some platforms leave open when they block 587 |
| `APP_MAIL_API_KEY`, `APP_MAIL_API_BASE_URL` | `transport=brevo` only; startup aborts with `brevo` selected and no key |
| `APP_MAIL_HEALTH_ENABLED` | Re-enable the `/actuator/health` SMTP probe. Off by default: it blocks for the full connect timeout and fails the whole endpoint wherever SMTP is unreachable |
| `APP_MONITOR_ENABLED`, `APP_MONITOR_INTERVAL` | Poll registered sites for threshold crossings, and how often. The monitor requires the instance to be awake; a sleeping host looks like a quiet fleet |
| `APP_MONITOR_EMONCMS_BASE_URL` | Emoncms instance the monitor reads feed metadata from |
| `APP_MONITOR_MAX_POWER`, `APP_MONITOR_MIN_VOLTAGE` | Application defaults (watts, volts) for sites with no site-specific threshold row. Starting points, not validated limits |
| `APP_MONITOR_STALE_AFTER`, `APP_MONITOR_CLEAR_DEADBAND`, `APP_MONITOR_CONCURRENCY` | Reading age beyond which no decision is made, clear-hysteresis fraction, and per-sweep parallelism |
| `APP_TRUSTED_PROXIES` | Comma-separated proxy addresses trusted for `X-Forwarded-For`. **Required behind a reverse proxy**, or every per-IP rate limit collapses into one bucket shared by all clients |
| `APP_AUTH_EXPOSE_VERIFICATION_CODE` | Dev only; return codes when mail is off |
| `APP_OPENAPI_ENABLED` | Enable `/v3/api-docs` and Swagger UI |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL. Required outside the `dev` profile; there is no fallback |
| `DB_POOL_MAX_SIZE` | Connection pool ceiling (default 20). Audited requests hold two connections at once |
| `REDIS_HOST`, `REDIS_PORT` | Redis |
| `SERVER_PORT` | HTTP port (default `8082`) |
| `APP_RATE_LIMIT_*` | Per-endpoint fixed-window limits |

See `.env.example` for the complete list.

## API overview

Public authentication:

- `POST /api/auth/register`
- `POST /api/auth/verify`
- `POST /api/auth/verify/resend`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/password/forgot`
- `POST /api/auth/password/reset`
- `POST /api/auth/oauth2/google`

Authenticated account:

- `GET/PATCH /api/me`
- `PUT /api/me/password`
- `POST /api/me/logout-all`
- `GET/POST /api/me/keys`
- `DELETE /api/me/keys/{id}`
- `GET /api/me/alarms`
- `GET/PUT /api/me/alarms/thresholds`, `/api/me/alarms/thresholds/{resource}`
- `GET /api/me/alarms/stream` (SSE)

Admin (ROLE_ADMIN):

- `GET/POST /api/admin/keys`
- `GET/PUT/DELETE /api/admin/keys/{id}`
- `PUT /api/admin/keys/{id}/emoncms-key`
- `GET /api/admin/keys/{id}/clients`
- `DELETE /api/admin/keys/{id}/clients/{userId}`
- `GET /api/admin/clients`
- `GET/POST /api/admin/users`
- `PATCH /api/admin/users/{id}/role`
- `POST /api/admin/users/{id}/disable`, `/enable`, `/revoke-sessions`
- `GET /api/admin/audit`
- `GET /api/admin/stats`
- `GET /api/admin/alarms` (filters: `active`, `kind`, `resource`, `page`, `size`)
- `GET /api/admin/alarms/summary`
- `GET /api/admin/alarms/stream` (SSE for the whole fleet)
- `GET /api/admin/alarms/thresholds`
- `PUT /api/admin/alarms/thresholds/{accessKeyId}`
- `DELETE /api/admin/alarms/thresholds/{accessKeyId}` (restores the application defaults)

Errors use RFC 7807 `ProblemDetail` with a stable `code` property and, for
unexpected failures, a `traceId`.

## Testing

```powershell
.\gradlew.bat clean test --console=plain
```

Integration tests use Testcontainers for PostgreSQL and Redis. The OpenAPI export
test writes `build/openapi.json` and checks that every v1 route is documented.

## Project layout

- `src/main/kotlin/ma/electricwave/controller` - REST and Thymeleaf admin pages
- `src/main/kotlin/ma/electricwave/service` - auth, account access, admin
- `src/main/kotlin/ma/electricwave/security` - JWT, IP resolution, error handlers
- `src/main/kotlin/ma/electricwave/domain` and `repository` - persistence
- `src/main/resources/db/migration` - Flyway schema
- `src/main/resources/templates/admin` - server-rendered admin pages
- `docs/DECISIONS.md` - assumptions and deviations
- `docs/FLUTTER_INTEGRATION.md` - client integration guide

## Docker image

```powershell
docker build -t electricwave-backend .
docker run --rm -p 8082:8082 --env-file .env electricwave-backend
```

Production requires real secrets and `SPRING_PROFILES_ACTIVE=prod`. OpenAPI and
Swagger UI are disabled in production by default.
