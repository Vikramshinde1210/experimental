package com.example.jwtauth.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class BankController {

    @PostMapping("/debit")
    public String debit(){
        return "Money Debited";
    }

    @GetMapping("/csrf")
    public CsrfToken getCsrf(HttpServletRequest request){
        return (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    }
}
