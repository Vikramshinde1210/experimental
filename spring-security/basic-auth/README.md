# Basic Auth

The oldest and simplest HTTP authentication scheme (RFC 7617). The client sends the
username and password on **every single request**, in a header:

```
Authorization: Basic base64(username:password)
```

Run it (port `8081`):

```
./gradlew :basic-auth:bootRun
```

Try it:

```
curl http://localhost:8081/public/hello                              # 200 - no creds needed
curl http://localhost:8081/private/hello                              # 401 - no creds sent
curl -u user:user-pass http://localhost:8081/private/hello            # 200
curl -u user:user-pass http://localhost:8081/private/admin            # 403 - wrong role
curl -u admin:admin-pass http://localhost:8081/private/admin          # 200
```

Users are hard-coded in `SecurityConfig#userDetailsService` (`admin`/`admin-pass`,
`user`/`user-pass`) via an in-memory `UserDetailsService` — no database needed for this
module, so the mechanism itself stays front and center.

## How it actually works

1. A request arrives with no `Authorization` header (or a bad one) at a protected route.
2. Spring Security's `BasicAuthenticationFilter` rejects it with `401 Unauthorized` and a
   `WWW-Authenticate: Basic realm="..."` response header. That header is a signal to
   browsers to pop up a native username/password dialog — this module returns JSON/plain
   text instead of a browser page, so that dialog is mostly relevant when testing in a
   browser rather than with `curl`/Postman.
3. The client resends the same request with `Authorization: Basic <base64(user:pass)>`.
4. `BasicAuthenticationFilter` decodes the header, and delegates to the configured
   `AuthenticationManager` (here, the default one backed by `UserDetailsService` +
   `PasswordEncoder`) to verify the password against the BCrypt hash.
5. On success, a `UsernamePasswordAuthenticationToken` is placed in the
   `SecurityContext` **for the duration of this request only** — nothing is cached
   server-side. Every subsequent request repeats steps 3-4 from scratch.

## Base64 is not encryption

`base64(username:password)` is *encoding*, not encryption — it is trivially reversible
by anyone who sees it (`echo <token> | base64 -d`). Basic Auth is only safe to use over
**HTTPS/TLS**, where the transport layer, not the scheme itself, protects the credentials
in transit. This POC runs over plain HTTP on `localhost` for convenience; never do that
in production.

## Why this module is stateless (`SessionCreationPolicy.STATELESS`) and has no CSRF protection

CSRF attacks exploit the fact that a browser *automatically* attaches a stored
credential (a session cookie) to requests, including ones an attacker's page tricked it
into making. Basic Auth doesn't rely on cookies — every request must explicitly carry
the `Authorization` header again — so there's nothing for a forged cross-site request to
piggyback on. That's why `csrf().disable()` is safe here, and why
`SessionCreationPolicy.STATELESS` is set explicitly: without it, Spring would still
create an `HttpSession` on first login by default even though nothing is stored in it,
which is wasted server memory for a mechanism that's inherently per-request.

## No logout, no expiry

There is no server-side session to invalidate and no token to expire — the "credential"
*is* the password, sent every time. The only way to "log out" a Basic Auth session in a
browser is to close the browser (it caches the credential for the realm) or change the
password server-side. This is one of the biggest practical downsides of Basic Auth, and
exactly the gap the next module — [`jwt-auth`](../jwt-auth/README.md) — addresses: mint
a short-lived, revocable-by-expiry token once, instead of resending the password
forever.
