package com.example.oidc.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

    @GetMapping("/public/hello")
    public String hello() {
        return "Hello, anyone. Visit /profile to trigger the Google OIDC login redirect.";
    }

    // OidcUser wraps a verifiable, signed ID token -- these claims came from Google
    // itself and can be checked offline (signature + issuer + audience + expiry),
    // unlike the oauth2-client module's OAuth2User, whose attributes are just a plain
    // JSON blob fetched over the network with no cryptographic guarantee attached.
    @GetMapping("/profile")
    public Map<String, Object> profile(OidcUser oidcUser) {
        OidcIdToken idToken = oidcUser.getIdToken();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("principalType", "OidcUser (backed by a signed ID token)");
        response.put("idTokenClaims", idToken.getClaims());
        response.put("issuer", idToken.getIssuer());
        response.put("subject", idToken.getSubject());
        response.put("audience", idToken.getAudience());
        response.put("issuedAt", idToken.getIssuedAt());
        response.put("expiresAt", idToken.getExpiresAt());
        response.put("rawIdTokenValue", idToken.getTokenValue());
        response.put("userInfoAttributes", oidcUser.getUserInfo() == null ? null : oidcUser.getUserInfo().getClaims());
        return response;
    }
}
