package com.example.sessionauth.repository;

import com.example.sessionauth.domain.User;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserStore {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;

    public UserStore(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        String hash = passwordEncoder.encode("password123");
        users.put("alice", new User("alice", hash, "USER"));
    }

    public User getUser(String username) {
        return users.get(username);
    }
}
