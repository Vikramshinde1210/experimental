# OAuth2 Client (login via Google, plain OAuth2 — no ID token)

OAuth2 (RFC 6749) is fundamentally an **authorization delegation** protocol: it lets an
app obtain a token that grants access to *some resource* (an API) on a user's behalf,
without ever seeing the user's password. It was not originally designed to answer "who is
this user" — that's what [OpenID Connect](../oidc/README.md) adds on top. This module
uses OAuth2 the way it's most commonly (mis)used in the wild: as a login mechanism, using
Google purely as an OAuth2 authorization server, with the `openid` scope deliberately
withheld so no identity token is ever issued.

## One-time setup: register an OAuth client with Google

1. Go to [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).
2. Create an **OAuth client ID** of type "Web application".
3. Add authorized redirect URI: `http://localhost:8083/login/oauth2/code/google`.
4. Export the generated credentials before running this module:
   ```
   export GOOGLE_CLIENT_ID=...
   export GOOGLE_CLIENT_SECRET=...
   ```

Run it (port `8083`):

```
./gradlew :oauth2-client:bootRun
```

Then open `http://localhost:8083/profile` **in a browser** (this flow requires an
interactive login redirect + consent screen — it cannot be exercised with `curl`).

## What happens when you hit `/profile`

1. `/profile` requires authentication, so Spring Security's `oauth2Login()` filter
   redirects the browser to Google's authorization endpoint with our client-id, the
   requested scopes, and a `redirect_uri` back to us.
2. You log in to Google (if not already) and approve the consent screen.
3. Google redirects the browser back to `/login/oauth2/code/google` with a one-time
   authorization `code`.
4. Spring Security exchanges that `code` (server-to-server, using our `client-secret`)
   for an **access token** at Google's token endpoint.
5. Because we asked only for `userinfo.email`/`userinfo.profile` (not `openid`), Google's
   token response contains **no `id_token`** — just an opaque access token. Spring
   Security's `OidcAuthorizationCodeAuthenticationProvider` requires an `id_token` to
   activate, so it never does; instead the plain `OAuth2LoginAuthenticationProvider`
   takes over.
6. That provider calls Google's userinfo REST endpoint *using the access token as a
   bearer credential*, and wraps the JSON response in a generic `OAuth2User`.
7. `ProfileController#profile` prints those attributes plus the raw access token itself
   — a redirect-based session is then maintained via a cookie so you don't repeat the
   Google round-trip on every request (see below re: CSRF).

## Why there's no ID token here

An **access token** is opaque by design — from the client's point of view it's just a
string to send back to the resource server; only the authorization server (Google) knows
or has to know what's inside. Verifying "who does this access token belong to" requires
either calling the provider back (as `ProfileController` does, indirectly, via
`OAuth2AuthorizedClient`) or the resource server introspecting the token server-side.
There is no offline, self-contained way to prove identity from an access token alone.

Compare this with the [`oidc`](../oidc/README.md) module, which requests the `openid`
scope and gets back a **signed, self-contained ID token** (a JWT) that any party can
verify offline — no callback to Google needed. That's the entire value OIDC adds.

## CSRF and sessions, this time it matters

Unlike the [`basic-auth`](../basic-auth/README.md) and [`jwt-auth`](../jwt-auth/README.md)
modules, `oauth2Login()` *does* establish a browser session (a cookie) after the redirect
dance completes, so you're not re-authenticating with Google on every request. That means
this module is the first one in the repo where CSRF protection is actually relevant —
Spring Security's CSRF filter is left at its default (enabled) here, unlike the other
three modules which explicitly disable it because they're stateless.
