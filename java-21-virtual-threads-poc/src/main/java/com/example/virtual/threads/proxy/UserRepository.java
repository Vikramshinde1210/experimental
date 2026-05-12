package com.example.virtual.threads.proxy;

import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    public String findUser(int id) {
        System.out.println(">>> DB called for user: " + id);
        return "User-" + id;
    }
}
