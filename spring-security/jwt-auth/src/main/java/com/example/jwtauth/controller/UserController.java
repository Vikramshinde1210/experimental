package com.example.jwtauth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.jwtauth.dto.AuthRequest;
import com.example.jwtauth.dto.Role;
import com.example.jwtauth.entity.UserEntity;
import com.example.jwtauth.repository.UserRepository;
import com.example.jwtauth.service.JwtService;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @GetMapping("/create")
    public void saveUser(@RequestParam String username,
                         @RequestParam String password,
                         @RequestParam String role){
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(username);
        userEntity.setPassword(passwordEncoder.encode(password));
        userEntity.setActive(true);
        userEntity.setRole(Role.valueOf(role));

        userRepository.save(userEntity);
    }


    @PostMapping("/authenticate")
    public String authenticate(@RequestBody AuthRequest authRequest){
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));
        if (authentication.isAuthenticated()){
            String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            return jwtService.generateToken(authRequest.getUsername(), role);
        }
        return null;
    }
}
