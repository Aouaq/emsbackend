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
  with `SPRING_PROFILES_ACTIVE=prod`. Two things now enforce that rather than trusting
  it: the Dockerfile pins `SPRING_PROFILES_ACTIVE=prod`, and
  `SecurityStartupValidator` refuses to start outside the `dev` profile while any
  published development secret is still configured. The literals it compares against
  live in `config/DevelopmentDefaults.kt` and must match `application-dev.yml` exactly.
- The base configuration no longer defaults `DB_*`; those values moved to
  `application-dev.yml`. A deployment missing them now fails to start instead of quietly
  trying the `compose.yaml` development credentials against localhost.
- `app.trusted-proxies` must be set whenever the application runs behind a reverse
  proxy. Left empty, every request appears to come from the proxy, so each per-IP rate
  limit becomes a single bucket shared by all clients - the eleventh login attempt from
  anyone in a minute is rejected. A blank value under the prod profile logs a warning
  rather than failing, since a directly exposed instance is legitimate.
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
  encrypted copy. Responses expose a short hint and, to an entitled caller, the key.
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
- The admin pages carry a `Content-Security-Policy` (and `Referrer-Policy`), applied by a
  path-scoped filter rather than through the security chain, because that chain also
  serves Swagger UI, which needs the inline scripts a useful policy forbids. HSTS is
  deliberately absent while the deployment terminates plain HTTP.

## Security filter chains

- Two chains, split by matcher. `/api/**` is stateless: without an explicit policy the
  default `IF_REQUIRED` stored a `SecurityContext` in a new `HttpSession` for every
  bearer-token call, so a polling mobile client accumulated sessions for the whole
  session timeout, and an administrator mobile token also minted a cookie that
  authenticated the admin UI. The second chain keeps form login, CSRF and sessions for
  the admin pages, API docs and actuator health.
- CSRF is disabled on the API chain rather than matcher-excluded on a shared one. The
  previous exclusion tested `request.requestURI`, which includes the servlet context
  path, so configuring one would have silently turned CSRF on for the API and routed its
  errors to the HTML entry point.
- `/error` is permitted on the admin chain because an error dispatch from any request,
  API requests included, is forwarded there.

## Registration conflicts

- Registration returns `409 CONFLICT` when the email already exists. This is a
  deliberate trade-off that avoids silently creating a second account; the
  specification asks for this behaviour to be recorded here.
- Registration reports whether the verification email was actually accepted, through
  `verificationCodeSent` and a matching message. It previously told the caller to check
  their inbox even when the send had failed, leaving them with no code and no hint that
  resending was the way out. The resend and forgot-password endpoints deliberately do
  *not* expose this: there, the outcome would distinguish an unknown account from one
  that exists and was mailed, which is the enumeration signal they exist to hide.
- Verifying a code checks the account before consuming an attempt. Five wrong guesses
  delete the stored code, so attempts are only ever spent on an account that really has
  a pending verification.

## Google sign-in on the web

- The web build needs a **different flow**, not different configuration.
  `google_sign_in_web` returns false from `supportsAuthenticate()` and throws
  `UnimplementedError` from `authenticate()`, because Google Identity Services requires
  the click to land on a button it renders itself. The signed-in account then arrives on
  `GoogleSignIn.authenticationEvents` instead of as a return value, so `AuthController`
  subscribes to that stream - but only where `supportsAuthenticate()` is false, since on
  Android the package emits the same event *and* returns the account, which would
  exchange one ID token twice.
- The same Web client ID goes in a different parameter per platform: `clientId` on the
  web, where the browser is the OAuth client, and `serverClientId` on Android, where the
  app authenticates as its own Android client and names the Web client the token is
  minted for. `google_sign_in_web` asserts `serverClientId == null`, so passing it on
  both - as the code did - left the web with no client ID at all.
- `web_only.dart` cannot be imported on Android or iOS, so the button is behind a
  conditional export on `dart.library.js_interop`.
- **CORS had to be added for any of this to matter.** There was none, so a browser's
  preflight for a JSON POST went unanswered and the request never reached a controller -
  which also means a browser failure leaves *no trace in the server log at all*, and log
  silence must not be read as "the request was not made".
- The origin serving the web app must also be registered under the Web OAuth client's
  **Authorized JavaScript origins** in Google Cloud. That is enforced by Google when its
  popup opens, not by anything here, so it fails late and looks like an app bug.

## Google sign-in accepts several client IDs

- `app.oauth2.google.client-id` is a comma-separated **list**. Each platform signs in
  with its own OAuth client and the ID token carries that client as its `aud`, so one
  person on Android, macOS and desktop presents three different audiences. The check was
  a single `contains(clientId)`, which meant adding any second platform broke it with
  "Google ID token was issued for another client" - a message that points at the token
  rather than at the configuration that is actually wrong.
- It is an allow-list, so every entry must be a client of this Google project. The
  audience claim is what proves a token was minted for us; accepting a client someone
  else controls would let them mint tokens this backend trusts.
- Client **IDs** only. Verifying an ID token is a signature and claims check against
  Google's public JWKS - no client secret is involved, and none belongs in the backend
  configuration. A secret is only needed by a desktop app doing an authorization-code
  exchange, and that exchange happens in the desktop app, not here.
- The installed-app ("Desktop app") client is listed but currently unused: the Flutter
  app has no desktop sign-in path. `google_sign_in` resolves only `_android`, `_ios` and
  `_web`, so Windows and Linux have no implementation at all, and a macOS build would
  need a macOS-type client rather than an installed-app one. Listing it now means the
  backend is ready the day that flow exists, and costs nothing meanwhile.

## SMTP send latency

- `spring.mail.properties.mail.smtp.localhost` and `spring.mail.properties.mail.from`
  are both set so JavaMail never has to resolve this machine's own hostname. It does
  that twice per send, and on a host with no PTR record for its LAN address each
  reverse lookup blocked for ~17s, making one registration take 42s:
    - the EHLO greeting calls `InetAddress.getLocalHost().getCanonicalHostName()`
      unless `mail.smtp.localhost` is set;
    - building the `Message-ID` header calls
      `InternetAddress.getLocalAddress()`, which falls back to
      `InetAddress.getLocalHost().getHostName()` unless `mail.from` is set.
  Measured per phase on the affected host: connect 18.3s -> 2.1s, send 18.4s -> 1.0s,
  total 37.0s -> 3.5s. Neither value is validated by the relay, and `MailService` sets
  the `From` header explicitly, so `mail.from` only supplies the Message-ID domain.
  This is not dev-machine-only tidying: any host whose own address has no reverse DNS
  record hits it, which includes plenty of container and cloud networks.
## Tests ignore local configuration

- The `test` task sets `spring.config.location` to the classpath locations only.
  Spring Boot's default search path includes `./` and `./config/`, so the gitignored
  `config/application.yml` a developer uses for real SMTP credentials was being loaded
  during tests. It sets `app.mail.enabled=true`, which makes `devVerificationCode`
  null, and `ApiTestSupport.register()` reads that field - 35 of 72 tests failed with
  an opaque `NullPointerException: read(...) must not be null`. Test outcomes must not
  depend on a file that is deliberately not committed.
- `@MobileApiIntegrationTest` additionally pins `app.mail.enabled=false` and
  `app.auth.expose-verification-code=true`. The suite genuinely depends on both, and
  `@SpringBootTest(properties)` outranks every config file, so stating them makes the
  requirement visible at the point of use instead of inherited from a default.

- Sends remain **synchronous**, so a registration request waits for the SMTP exchange
  (~3s against Gmail). That is deliberate: `verificationCodeSent` in the response is a
  fact rather than a promise, which is what lets the client tell the user to check
  their inbox honestly. Moving the send to `@Async` would make registration return in
  milliseconds but reduce that flag to a guess, so it should only be done together with
  a durable outbox the client can query.

## Authentication and account lifecycle

- A disabled account is checked after credential verification and returns
  `403 ACCOUNT_DISABLED`; an unverified account returns `403 ACCOUNT_NOT_VERIFIED`.
  Refresh applies the same check.
- Authenticated calls cannot rely on that check alone, because an access token keeps
  asserting the `roles` and `uid` it was minted with until it expires. Disabling an
  account, changing a role, resetting a password, revoking sessions and
  `POST /api/me/logout-all` therefore also write a revocation marker to Redis
  (`revoked:user:<id>`, kept for one access-token lifetime), and the `JwtDecoder` rejects
  any token issued before it. Without this, a demoted administrator kept administrator
  authority, and a disabled client kept `/api/me` access, for up to
  `app.jwt.expiration`.
- Ordering a token against a revocation uses a non-standard `iat_ms` claim, because the
  standard `iat` carries only seconds and a revocation almost always falls in the same
  second as the token it must kill. A token minted in the same *millisecond* as the
  revocation is kept, so that signing in again immediately afterwards works.
- A revoked token yields `401`, not `403 ACCOUNT_DISABLED`, and refreshing yields `401`
  too since the refresh family is revoked with it. The client is pushed back to sign-in,
  and it is the sign-in attempt that names the cause. `MeService` additionally re-reads
  `disabledAt` on every authenticated call, so the documented `403 ACCOUNT_DISABLED`
  contract still holds if the Redis marker is ever lost.
- Enabling a user evicts the account-access cache, mirroring what disabling does;
  otherwise a cached negative result outlived the fix that was meant to clear it.
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
- Audit writes keep their own `REQUIRES_NEW` transaction, so they survive a rollback of
  the operation being audited, but failures are now logged and swallowed rather than
  propagated: an audit problem must not turn a successful login into a 500. The boundary
  lives in a separate `AuditWriter` bean, since catching the failure in the same class
  would have put the self-call inside the caller transaction.
- Because an audited operation holds two connections at once (its own transaction plus
  the nested audit write), the Hikari pool is sized at 20 rather than 10; at 10 it
  deadlocked at five concurrent audited requests.
- Admin user search escapes LIKE metacharacters in the query term, so searching for a
  bare wildcard no longer matches every row.
- Toggling the active flag of a key is a single `update ... set is_active = not
  is_active` rather than a read, negate and write back, so two concurrent toggles cannot
  both act on the same observed value.
- Listing users and clients resolves linked resources for the whole page in one query.
  Mapping the lazy `accessKeys` collection per row cost one query per user. The number of
  clients returned is still unbounded; paginating it would change a documented response
  shape and is left as a follow-up.
- Admin page failures render as HTML through a `@ControllerAdvice` scoped to
  `ma.electricwave.web`. The global `@RestControllerAdvice` advises those controllers too,
  so an unexpected error previously reached the browser as raw problem+JSON.
- Promotion to `ADMIN` removes the user's Emoncms links. It remains a significant grant,
  because an admin can read every registered Emoncms key through `GET /api/admin/keys`;
  the existing guards (no self-role-change, the last admin cannot be demoted or disabled)
  do not limit that, so who may promote is the control that matters. Disabling a user and
  changing a role revoke refresh sessions; enabling a user does not restore old
  sessions.
- Key replacement keeps existing client links but invalidates the old claim key.
  Admin key updates, replacement and deletion evict both the account-access
  cache for the affected resource.

## Emoncms access

- Because the client reads Emoncms directly, `GET /api/me/keys` and `GET /api/admin/keys`
  return the decrypted Emoncms key to a caller entitled to it - their own linked accounts
  for a client, every account for an admin. This gives up the property that the key never
  leaves the server, and the trade-off is deliberate: it is the only way an app that talks
  to Emoncms itself can recover its accounts after a reinstall, since nothing is left on
  the device. The consequence to keep in mind is that disabling an account no longer
  withdraws data access from a device that already holds the key; only rotating the key
  does that.

- The read-only Emoncms **gateway was removed**. It proxied three fixed feed paths
  through `/api/gateway/**`, holding the key server-side so the device never saw it.
  That design was abandoned once the client went back to reading Emoncms directly, and
  the endpoints then sat unused for some time: nothing called them, and the only other
  consumer of `app.emoncms.base-url` was an admin key validation that ships disabled.
  Keeping an unused authenticated proxy to a private instance is attack surface for no
  benefit, so the controller, service, HTTP client and their configuration are gone.
  Recover them from git history if the web build ever needs a same-origin proxy to get
  around the Emoncms CORS restriction.
- `AccountAccessService` went with it. The gateway was the only caller of its
  `canAccess`, so what remained was a Caffeine cache that a dozen call sites dutifully
  evicted and nothing ever read. Its repository query `findAccessible` and the Caffeine
  dependency itself were orphaned by the same removal and are gone too.
- **Links are now a record, not an authorization.** `user_access_keys` still says which
  accounts a client has claimed, and `/api/me/keys` still returns only that client's own
  accounts, but no request is authorized by consulting a link any more. If a future
  feature needs the backend to decide whether a user may read a resource, that check has
  to be reintroduced deliberately rather than assumed to be there.
## Testing and delivery

- Testcontainers currently uses `postgres:latest` and `redis:latest`, while
  `compose.yaml` pins PostgreSQL 16 and Redis 7. Pinning the test images is a
  follow-up cleanup; no production data exists in tests.
- The OpenAPI export test writes `build/openapi.json` and verifies every v1
  route plus the `ProblemDetail` schema. The schema is added by a customizer
  because Spring's `ProblemDetail` model does not expose the custom `code` and
  `traceId` properties.
