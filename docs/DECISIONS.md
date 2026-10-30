# ElectricWave access backend - decisions and assumptions

This file records every assumption or deviation from the v1 specification.

## Repository and delivery

- The project was not under version control. A git repository was initialized and
  the pre-rework state was committed as a baseline so each implementation phase
  can be committed separately.
- The specification requires `./gradlew clean test` after every phase. On this
  Windows workstation the equivalent command is `.\gradlew.bat clean test` with
  `JAVA_HOME` pointing at JDK 17.

## Configuration

- `spring.profiles.default=dev` is set in `application.yml`. Running without an
  explicit profile therefore uses the development defaults; production must run
  with `SPRING_PROFILES_ACTIVE=prod`.
- Development-only defaults (JWT secret, bootstrap admin credentials, exposed
  verification code, OpenAPI UI) live in `application-dev.yml`. The base and
  production configuration contain no working secrets or default admin password.
- Environment variable names follow the specification: `APP_JWT_SECRET`,
  `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD`, `APP_OAUTH2_GOOGLE_CLIENT_ID`,
  `APP_MAIL_ENABLED`, `APP_MAIL_FROM`, `APP_AUTH_EXPOSE_VERIFICATION_CODE`,
  `DB_*`, `REDIS_*`. Rate limits use `APP_RATE_LIMIT_*`.

## Product model

- The claim code is the real Emoncms read key. The backend never generates or
  rotates claim codes, QR codes or links; it only matches and links an Emoncms
  key that an administrator registered for an account.
- Registration and Google sign-in never accept access keys. Clients start with
  zero linked accounts and claim them later through `POST /api/me/keys`.
- Emoncms keys are stored as a SHA-256 hash for matching and as an AES-256-GCM
  encrypted copy for the gateway. Responses expose only a short hint and whether
  a key is configured.
- `key_hash` is a `char(64)` column as specified. Hibernate is told to expect a
  CHAR JDBC type (`@JdbcTypeCode(SqlTypes.CHAR)`) so schema validation succeeds.
- The development profile contains a fixed base64 32-byte crypto master key so
  encrypted keys survive restarts locally. Outside `dev`, `APP_CRYPTO_MASTER_KEY`
  is mandatory; a blank or non-32-byte value aborts startup.
- Access keys are administrator-provisioned. Creating or promoting an
  administrator is allowed for administrators, subject to the guards described
  in the specification (no self role changes, at least one active administrator
  must remain).

## Migration and local database

- The existing development database contains Flyway history for the previous
  `V1__core_schema.sql`. Replacing it with the clean v1 schema will invalidate
  that history. The local database volume must be recreated (or the schema
  dropped) before starting the application after the schema phase. No production
  data exists at this point.

## Transport

- Plain HTTP is used for now. Every external base URL is configurable so HTTPS
  and cookie flags can be enabled without code changes.

## Registration conflicts

- Registration returns `409 CONFLICT` when the email already exists. This is a
  deliberate trade-off that avoids silently creating a second account; the
  specification asks for this behaviour to be recorded here.

## Authentication and account lifecycle

- A disabled account is checked after credential verification and returns
  `403 ACCOUNT_DISABLED`; an unverified account returns `403 ACCOUNT_NOT_VERIFIED`.
  Refresh and authenticated calls use the same check.
- The first password login for a Google-created account upgrades an unverified
  local account by clearing the password and setting `authProvider=GOOGLE`. A
  later password reset converts the account back to `LOCAL` and revokes sessions.
- Refresh-token reuse revokes the whole token family, including the token that
  was rotated in immediately before the reuse. This is intentionally strict.
- Five failed verification or reset attempts delete the stored code, forcing a
  new code request. Password-reset requests are neutral for unknown, unverified
  and disabled accounts to avoid account enumeration.

## Admin API

- `GET /api/admin/users` now returns `PageResponse<AdminUserResponse>` instead of
  a bare list. This is a breaking shape change from the pre-v1 admin page; page
  size is capped at 100 and the response contains `content`, `page`, `size`,
  `totalElements` and `totalPages`.
- Admins cannot disable their own account (409), in addition to the specified
  no-self-role-change and last-admin guards.
- Admin actions are audited with the authenticated admin email in `actorEmail`
  because the security principal carries no user id. `actorUserId` remains null
  for these events.
- Promotion to `ADMIN` removes the user's Emoncms links. Disabling a user and
  changing a role revoke refresh sessions; enabling a user does not restore old
  sessions.
- Key replacement keeps existing client links but invalidates the old claim key.
  Admin key updates, replacement and deletion evict both the account-access
  cache and the gateway response cache for the affected resource.

## Gateway

- The gateway exposes only three fixed Emoncms paths and forwards only the
  documented parameters for each path. Any unknown parameter, including a
  client-supplied `apikey`, is rejected with `400 VALIDATION_FAILED`.
- Access is checked before the 750 ms response cache is consulted. The cache is
  shared by resource/path/query, not by user, because every caller has already
  been authorized for the resource.
- Admins do not bypass the link requirement on the gateway; admin access is
  limited to account management. This keeps the data path least-privilege.
- `app.emoncms.validate-on-save=true` validates the key against
  `feed/list.json`. Any upstream/configuration failure during that validation is
  mapped to `400 INVALID_KEY` for the admin save operation.
- Caffeine `invalidateIf` is not available in the selected version, so cache
  eviction uses `cache.asMap().keys.removeIf { ... }`.

## Testing and delivery

- Testcontainers currently uses `postgres:latest` and `redis:latest`, while
  `compose.yaml` pins PostgreSQL 16 and Redis 7. Pinning the test images is a
  follow-up cleanup; no production data exists in tests.
- The OpenAPI export test writes `build/openapi.json` and verifies every v1
  route plus the `ProblemDetail` schema. The schema is added by a customizer
  because Spring's `ProblemDetail` model does not expose the custom `code` and
  `traceId` properties.
