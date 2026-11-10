# Flutter integration guide

Base URL in development: `http://localhost:8082`. All requests and responses use JSON.

## Authentication flow

1. Register with `POST /api/auth/register` using `email`, `password` and optional
   `fullName`. Do not send an Emoncms key.
2. Ask the user for the 6-digit code and call `POST /api/auth/verify`.
   In development, the code may be present in the response when mail is disabled.
3. Call `POST /api/auth/login` and store the returned `token` (access JWT) and
   `refreshToken` securely. Access tokens expire after 15 minutes.
4. Refresh with `POST /api/auth/refresh` before expiry. Refresh tokens are
   single-use; always replace the stored pair with the response.
5. Log out with `POST /api/auth/logout` or everywhere with
   `POST /api/me/logout-all`.

If refresh returns `401`, clear the session and show the login screen. If a
refresh token is reused, the entire token family is revoked; never replay an old
refresh token from concurrent requests. Serialize refresh calls in the app.

Native Google sign-in:

1. Obtain a Google ID token in Flutter.
2. Send it as `idToken` to `POST /api/auth/oauth2/google`.
3. Store the returned access/refresh pair exactly like password login.

Password reset:

- `POST /api/auth/password/forgot` always returns `202` for valid requests.
- `POST /api/auth/password/reset` with `email`, `code` and `newPassword` revokes
  every existing refresh token. Return the user to login afterward.

## Claiming an Emoncms account

The QR code or deep link contains the account's Emoncms read key. Extract it in
Flutter and call:

```http
POST /api/me/keys
Authorization: Bearer <accessToken>
Content-Type: application/json

{"key":"<emoncms-read-key>"}
```

The key is 16-64 letters/digits. The backend matches an active account registered
by an administrator. Unknown, malformed and inactive keys all return the same
`400 INVALID_KEY`, so do not expose different messages. A duplicate link returns
`409 CONFLICT`.

`GET /api/me/keys` returns `id`, `resource`, `description`, `keyHint`,
`emoncmsKeyConfigured`, `isActive` and `linkedAt`. The plaintext key is never
returned. `DELETE /api/me/keys/{id}` removes only this user's link.

## Reading feed data

This service does not proxy Emoncms. `GET /api/me/keys` (a client's claimed accounts)
and `GET /api/admin/keys` (every account, for an admin) return the Emoncms read key
itself, and the app talks to Emoncms directly with it.

An Emoncms gateway used to live at `/api/gateway/**`. It was removed; those paths now
return `404`.

## Error handling

Every error is a `ProblemDetail` object. Read the `code` property, not the HTTP
status alone:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid Emoncms key",
  "instance": "/api/me/keys",
  "code": "INVALID_KEY",
  "traceId": "..."
}
```

Common codes: `VALIDATION_FAILED`, `INVALID_CREDENTIALS`,
`ACCOUNT_NOT_VERIFIED`, `ACCOUNT_DISABLED`, `INVALID_CODE`, `INVALID_KEY`,
`INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_REUSED`, `RATE_LIMITED`, `UNAUTHORIZED`,
`FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `UPSTREAM_ERROR`, `UPSTREAM_TIMEOUT`,
`NOT_CONFIGURED`, `INTERNAL_ERROR`.

On `401`, try one refresh only if the request was authenticated. Do not refresh
on public login/register/verify errors. On `429`, back off using `Retry-After`.
On `403 ACCOUNT_DISABLED`, do not retry; tell the user to contact an administrator.

## Profile

- `GET /api/me` returns `id`, `email`, `fullName`, `role`, `provider`,
  `verified`, `createdAt`, `lastLoginAt`.
- `PATCH /api/me` updates `fullName`.
- `PUT /api/me/password` requires `currentPassword` and `newPassword` and revokes
  all refresh sessions. Log the user in again afterward.

## Admin pages

The server-rendered admin UI is at `/admin/dashboard`, `/admin/users` and
`/admin/audit`. It uses form login and CSRF; it is separate from the Flutter API.
