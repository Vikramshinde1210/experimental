package com.example.oauth2client.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

    @GetMapping("/public/hello")
    public String hello() {
        return "Hello, anyone. Visit /profile to trigger the Google OAuth2 login redirect.";
    }

    // OAuth2User only exposes whatever attributes the provider's userinfo endpoint
    // returned (name/email/picture here) -- there is no signed identity assertion,
    // just data fetched with a bearer access token. Compare with the oidc module's
    // /profile, which reads signed ID token claims instead.
    @GetMapping("/profile")
    public Map<String, Object> profile(OAuth2User oAuth2User,
                                        @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("principalType", "OAuth2User (no ID token was issued -- see README)");
        response.put("attributes", oAuth2User.getAttributes());
        response.put("accessTokenValue", accessToken.getTokenValue());
        response.put("accessTokenExpiresAt", accessToken.getExpiresAt());
        response.put("accessTokenScopes", accessToken.getScopes());
        response.put("secondsUntilExpiry",
            accessToken.getExpiresAt() == null ? null : accessToken.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
        return response;
    }
}
