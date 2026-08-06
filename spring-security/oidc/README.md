# OpenID Connect (OIDC) — login via Google, the "correct" way

OpenID Connect is a thin identity layer built **on top of** OAuth2. It standardizes the
piece OAuth2 deliberately left out: proving *who the user is*, not just what they've
authorized you to access. Concretely, OIDC adds:

- a mandatory `openid` scope that signals "also give me identity",
- a new token type, the **ID token** — a signed JWT with standard identity claims,
- a standard `/userinfo` endpoint,
- and standard discovery metadata (`/.well-known/openid-configuration`).

This module registers with Google exactly like [`oauth2-client`](../oauth2-client/README.md)
does, but without stripping the `openid` scope — so Google actually issues an ID token,
and Spring Security's OIDC support activates automatically.

## One-time setup: register an OAuth client with Google

1. Go to [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).
2. Create an **OAuth client ID** of type "Web application" (or reuse the one from
   `oauth2-client`, just add another redirect URI to it).
3. Add authorized redirect URI: `http://localhost:8084/login/oauth2/code/google`.
4. Export the credentials before running this module:
   ```
   export GOOGLE_CLIENT_ID=...
   export GOOGLE_CLIENT_SECRET=...
   ```

Run it (port `8084`):

```
./gradlew :oidc:bootRun
```

Then open `http://localhost:8084/profile` **in a browser** (interactive login redirect —
cannot be exercised with `curl`).

## What's different from the `oauth2-client` module

`SecurityConfig` in both modules is nearly identical — `oauth2Login(Customizer
.withDefaults())` either way. The entire behavioral difference comes down to one thing:
whether `openid` is in the requested scope. That's deliberate — it's the clearest way to
show that OIDC isn't a separate protocol you "switch to," it's OAuth2 plus one scope plus
one extra token:

| | `oauth2-client` | `oidc` |
|---|---|---|
| Scope requested | `userinfo.email`, `userinfo.profile` | `openid`, `profile`, `email` (default) |
| Token(s) returned | access token only | access token **+ ID token** |
| Spring principal type | `OAuth2User` | `OidcUser` |
| Proving identity | call Google's userinfo endpoint again | verify the ID token signature offline |

## The ID token

`/profile` prints `oidcUser.getIdToken()`'s claims — standard ones include:

- `iss` — issuer (`https://accounts.google.com`): who signed this token.
- `sub` — subject: Google's stable, unique identifier for this user. Use this, not
  email, as the durable key for "who logged in" — emails can change/be reused.
- `aud` — audience: our own `client-id`. Rejecting tokens whose `aud` doesn't match you
  is what stops a token minted for a *different* app being replayed against yours.
- `iat` / `exp` — issued-at / expiry.
- `email`, `name`, `picture` — the actual profile claims we asked for.

Because the ID token is signed (Google publishes its public keys at
`/.well-known/openid-configuration` → `jwks_uri`), any party holding it can verify `iss`
+ signature + `aud` + `exp` **without calling Google again** — a structural improvement
over the opaque access token in the `oauth2-client` module, which proves nothing about
identity by itself and requires a network round-trip to resolve.

## `/userinfo` vs. ID token claims

Spring also exposes `oidcUser.getUserInfo()`, populated by calling the standard OIDC
`/userinfo` endpoint — this can carry additional/fresher claims than the ID token (which
is fixed at the moment it was issued). `/profile` prints both so you can compare them
side by side.
