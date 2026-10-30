# ElectricWave Access Backend

Kotlin/Spring Boot backend for the ElectricWave Flutter application. It provides
email/password and native Google authentication, account administration, and a
read-only, whitelisted gateway to a private Emoncms instance.

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
The OpenAPI document is at `/v3/api-docs`.

## Configuration

All configuration is environment-driven. Do not deploy the development defaults.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` (default) or `prod` |
| `APP_JWT_SECRET` | HS256 secret, at least 32 bytes |
| `APP_CRYPTO_MASTER_KEY` | Base64 32-byte AES-256-GCM key |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | Bootstrap admin |
| `APP_OAUTH2_GOOGLE_CLIENT_ID` | Google ID-token audience |
| `APP_MAIL_ENABLED`, `APP_MAIL_FROM` | SMTP delivery |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP server |
| `APP_EMONCMS_BASE_URL` | Upstream Emoncms URL |
| `APP_EMONCMS_VALIDATE_ON_SAVE` | Validate keys with `feed/list.json` when an admin saves |
| `APP_TRUSTED_PROXIES` | Comma-separated proxy addresses trusted for `X-Forwarded-For` |
| `APP_AUTH_EXPOSE_VERIFICATION_CODE` | Dev only; return codes when mail is off |
| `APP_OPENAPI_ENABLED` | Enable `/v3/api-docs` and Swagger UI |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
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

Gateway (linked account required):

- `GET /api/gateway/{resource}/feed/list.json`
- `GET /api/gateway/{resource}/feed/data.json?id=&start=&end=&interval=&skipmissing=`
- `GET /api/gateway/{resource}/feed/timevalue.json?id=`

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
- `src/main/kotlin/ma/electricwave/service` - auth, account access, gateway, admin
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
