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
