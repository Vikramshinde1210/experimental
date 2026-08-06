package com.example.jwtauth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BasicController {

    @GetMapping("/hello")
    public String hello(){
        return "Hello world";
    }

    @GetMapping("/hi")
    public String hi(){
        return "Hello world";
    }
}
