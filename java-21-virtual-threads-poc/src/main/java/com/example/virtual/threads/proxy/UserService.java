package com.example.virtual.threads.proxy;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // @Transactional uses proxy — same mechanism as @Cacheable
    @Transactional
    public String getUser(int id) {
        System.out.println(">>> getUser() called, class = "
            + this.getClass().getName());
        return userRepository.findUser(id);
    }

    // ❌ self invocation — proxy bypassed
    public String getUserInternal(int id) {
        System.out.println(">>> getUserInternal() calling getUser() on `this`");
        return getUser(id);  // this.getUser() — proxy skipped!
    }
}
