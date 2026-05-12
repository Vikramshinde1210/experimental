package com.example.virtual.threads.proxy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProxyController {

    private final UserService userService;

    // Spring injects the PROXY not the real UserService
    public ProxyController(UserService userService) {
        this.userService = userService;
    }

    // Call 1 — through proxy ✅
    @GetMapping("/correct")
    public String correct() {
        System.out.println("Injected class: "
            + userService.getClass().getName());
        return userService.getUser(1);
    }

    // Call 2 — self invocation ❌
    @GetMapping("/broken")
    public String broken() {
        return userService.getUserInternal(1);
    }
}
