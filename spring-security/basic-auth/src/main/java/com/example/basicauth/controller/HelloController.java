package com.example.basicauth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/public/hello")
    public String publicHello() {
        return "Hello, anyone. No credentials required.";
    }

    @GetMapping("/private/hello")
    public String privateHello(Authentication authentication) {
        return "Hello, " + authentication.getName() + ". You authenticated with Basic Auth.";
    }

    @GetMapping("/private/admin")
    public String adminOnly(Authentication authentication) {
        return "Hello, " + authentication.getName() + ". You have ROLE_ADMIN.";
    }
}
