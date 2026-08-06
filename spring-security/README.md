# Spring Security POC — auth mechanisms, one module each

A multi-module Gradle project. Each submodule is a **standalone, independently runnable**
Spring Boot app demonstrating one HTTP authentication/authorization mechanism in
isolation, with its own port and its own README going into that mechanism's specifics.

| Module | Port | Mechanism | Stateless? | External IdP? | Key starter |
|---|---|---|---|---|---|
| [`basic-auth`](basic-auth/README.md) | 8081 | HTTP Basic Auth | Yes | No | `spring-boot-starter-security` |
| [`jwt-auth`](jwt-auth/README.md) | 8082 | JWT + role/permission RBAC + method security | Yes | No (Postgres-backed users) | `spring-boot-starter-security` + `jjwt` |
| [`oauth2-client`](oauth2-client/README.md) | 8083 | OAuth2 login (no ID token) | No (session cookie) | Yes (Google) | `spring-boot-starter-oauth2-client` |
| [`oidc`](oidc/README.md) | 8084 | OpenID Connect (ID token) | No (session cookie) | Yes (Google) | `spring-boot-starter-oauth2-client` |

## Running a module

```
./gradlew :basic-auth:bootRun
./gradlew :jwt-auth:bootRun        # needs local Postgres, see jwt-auth/README.md
./gradlew :oauth2-client:bootRun   # needs GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET
./gradlew :oidc:bootRun            # needs GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET
```

They run independently and can all be started at once (different ports, no shared
state) — there's no "main" app; pick whichever mechanism you're studying.

## The progression, in one paragraph each

**Basic Auth** — prove you know a shared secret (the password) on *every single
request*, sent as a base64-encoded header. Simplest possible scheme; no session, no
logout, no expiry — and only safe over TLS, since base64 isn't encryption. See
[`basic-auth`](basic-auth/README.md).

**JWT** — stop resending the password. Authenticate once, receive a signed, time-boxed
token, and present *that* on every later request instead. The token is self-contained
(claims + signature), so any node holding the shared signing secret can verify it without
a database lookup or calling back to an auth server. This POC layers role- and
permission-based authorization plus method-level security (`@PreAuthorize`/
`@PostAuthorize`) on top. See [`jwt-auth`](jwt-auth/README.md).

**OAuth2** — delegate *access*, not identity. Instead of your app owning a password
database at all, a third party (Google) authenticates the user and hands your app a
scoped access token to call an API on the user's behalf. Using that access token as a
stand-in for "the user is logged in" — which is what `oauth2Login()` does — works, but
OAuth2 itself makes no identity guarantee: the token is opaque, and proving who it
belongs to means calling the provider back. See [`oauth2-client`](oauth2-client/README.md).

**OpenID Connect** — OAuth2 plus an actual identity guarantee. Add the `openid` scope
and the provider also hands back a signed **ID token** — a JWT any party can verify
offline (issuer, signature, audience, expiry) without another network round-trip. This is
the standard way to build "Sign in with Google/Microsoft/etc." because it answers the
question OAuth2 alone can't: not just "can this token call an API" but "who is this."
See [`oidc`](oidc/README.md).

## Concept glossary (quick reference — see each module's README for depth)

- **Stateless vs. session-based** — `basic-auth` and `jwt-auth` require the client to
  attach proof (password or token) to every request; `oauth2-client` and `oidc`
  establish a session cookie after the login redirect completes.
- **CSRF** — only relevant where a session cookie is auto-attached by the browser.
  Disabled explicitly in `basic-auth`/`jwt-auth` (no cookie exists); left at Spring's
  default (enabled) in `oauth2-client`/`oidc`.
- **Access token vs. ID token** — an access token authorizes *calling an API*; an ID
  token asserts *who the user is*. `oauth2-client` only ever sees the former;
  `oidc` gets both.
- **RBAC (role-based access control)** — demonstrated in `jwt-auth`: roles own sets of
  fine-grained permissions, both exposed as Spring `GrantedAuthority`s.
