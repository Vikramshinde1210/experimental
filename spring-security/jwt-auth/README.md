# JWT Auth + RBAC + Method Security

A stateless mechanism: the client authenticates **once** with a username/password to get
a signed token, then presents that token on every subsequent request instead of the
password. Also demonstrates role/permission-based authorization and Spring's method-level
security annotations.

Requires a local Postgres reachable per `src/main/resources/application.properties`
(`jdbc:postgresql://localhost:5432/mydb`, user `postgres`) — `spring.sql.init.mode=always`
runs `schema.sql` on startup to create the `users` table.

Run it (port `8082`):

```
./gradlew :jwt-auth:bootRun
```

## Flow

```
1. GET  /user/create?username=alice&password=pw&role=ROLE_ADMIN   -> creates a user row (password BCrypt-hashed)
2. POST /user/authenticate  {"username":"alice","password":"pw"}  -> returns a signed JWT
3. GET  /rooms  with header  Authorization: Bearer <token>         -> JwtFilter authenticates the request
```

`role` must be one of `ROLE_ADMIN`, `ROLE_STAFF`, `ROLE_GUEST` (see `dto/Role.java`).

## JWT structure

A JWT is three base64url segments joined by dots: `header.payload.signature`.

- **Header** — `{"alg":"HS256"}`: which algorithm signed this token.
- **Payload (claims)** — here: `sub` (username), `iat` (issued-at), `exp` (expiry, 30
  minutes from issue — see `JwtService#generateToken`), and a custom `Role` claim.
- **Signature** — `HMAC-SHA256(header + "." + payload, SECRET)`. Anyone can *read* the
  payload (it's just base64, not encrypted — never put secrets in claims), but only
  someone holding `SECRET` can produce a signature that verifies. This is what makes the
  token tamper-evident: change one character of the payload and the signature no longer
  matches.

`JwtService` centralizes signing (`generateToken`) and verification
(`verifySignatureAndExtractClaims`, `isExpired`) around a single shared `SECRET` (HMAC —
same key signs and verifies; contrast with RS256/ES256 asymmetric signing where only the
issuer holds the private key, used by the `oidc` module's ID tokens).

## Authentication flow, end to end

1. `UserController#authenticate` hands the submitted username/password to Spring's
   `AuthenticationManager` (a `ProviderManager` wrapping a `DaoAuthenticationProvider`,
   configured in `SecurityConfig`), which loads the user via `UserService`
   (`UserDetailsService`) and checks the password against the BCrypt hash.
2. On success, it mints a JWT via `JwtService.generateToken`, embedding the user's role.
3. The client stores that token and sends it as `Authorization: Bearer <token>` on every
   later request — no password is transmitted again.
4. `JwtFilter` (a `OncePerRequestFilter` registered via `addFilterBefore(jwtFilter,
   UsernamePasswordAuthenticationFilter.class)` in `SecurityConfig`) runs before Spring's
   normal auth filter: it extracts the bearer token, verifies the signature, checks
   expiry, and — if valid — builds a `UsernamePasswordAuthenticationToken` carrying the
   role + permissions as `GrantedAuthority`s, and places it in the `SecurityContext`.
   From that point on, the request looks "authenticated" to the rest of Spring Security
   exactly as if a session had established it — except nothing is stored server-side;
   the token itself is the complete proof, re-verified from scratch on every request.

## Role vs. Permission model

`Role` (`dto/Role.java`) is an enum where each constant *owns* a `Set<Permission>`
(`dto/Permission.java`, e.g. `ROOM_ADD`, `ROOM_VIEW`, `ROOM_VIEW_ALL`). Both the role
itself and its permissions are added as `GrantedAuthority`s (`JwtFilter`,
`UserService#loadUserByUsername`), so authorization rules can check either the coarse
role (`hasRole("ADMIN")`) or the fine-grained permission (`hasAuthority("ROOM_ADD")`) —
`RoomController` uses the permission form for `addRoom`.

## Method security (`RoomController`)

- `@PreAuthorize("hasAuthority('ROOM_ADD')")` — evaluated **before** the method runs;
  the method body never executes if the check fails.
- `@PreAuthorize("hasAnyRole(...)")` + `@PostAuthorize("returnObject.assignedTo ==
  authentication.name")` on `getRoomById` — the method runs first, *then* Spring checks
  a condition against the returned object (here: the caller may only see a room if it's
  assigned to them) — useful when the authorization decision depends on data you don't
  have until you've fetched it.
- `@PermitAll` on `getRooms` — no restriction beyond whatever the filter chain already
  requires.

## Why CSRF is disabled here (`csrf().disable()` in `SecurityConfig`)

CSRF protection matters when a browser **automatically** attaches a stored credential —
a session cookie — to requests, letting an attacker's page ride on it. This module never
issues a cookie: the bearer token must be explicitly attached to every request by the
client, so there's nothing for a cross-site request to piggyback on. `BankController`'s
`/debit` and `/csrf` endpoints are left in as a reference (`/csrf` will return `null`
here, since no `CsrfToken` is generated) to make that contrast visible — go look at the
[`oauth2-client`](../oauth2-client/README.md) and [`oidc`](../oidc/README.md) modules for
where a session cookie (and therefore CSRF protection) actually comes back into play.

## How this config evolved (and why the final version looks the way it does)

The filter chain in `SecurityConfig` went through several iterations while building this
module; each step is worth understanding even though only the last one remains in code:

1. **Default form login** — do nothing beyond `@EnableWebSecurity` and Spring Security
   auto-configures a login *form* and requires authentication on every request. Fine for
   a browser app, wrong for an API.
2. **Explicit `httpBasic()`** — swap the form for HTTP Basic (see the
   [`basic-auth`](../basic-auth/README.md) module for what that mechanism actually does).
   Still requires resending credentials every request.
3. **`csrf().disable()` + path matchers** — start carving out public routes
   (`/user/**` for signup/login) from `anyRequest().authenticated()`, and drop CSRF since
   Basic Auth doesn't need it.
4. **Add `JwtFilter` before `UsernamePasswordAuthenticationFilter`** — replace Basic Auth
   with bearer-token verification; now authentication is driven entirely by a token
   nobody has to resend a password for.
5. **Layer on RBAC** (the version in code now) — `JwtFilter` attaches role +
   permission authorities from the token's claims, so `@PreAuthorize`/`@PostAuthorize`
   in `RoomController` can make fine-grained decisions per endpoint/response, not just
   "authenticated or not."

Each step removed a limitation of the one before it: no more browser-only form, no more
resending a password on every call, no more all-or-nothing authorization.
