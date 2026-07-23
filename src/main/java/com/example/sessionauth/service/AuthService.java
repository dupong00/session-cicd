package com.example.sessionauth.service;

import com.example.sessionauth.domain.User;
import com.example.sessionauth.repository.UserStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserStore userStore;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserStore userStore, PasswordEncoder passwordEncoder) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
    }

    public User authenticate(String username, String password) {
        User user = userStore.getUser(username);

        if (user == null) {
            return null;
        }

        if (!passwordEncoder.matches(password, user.getPasswordHashed())){
            return null;
        }

        return user;
    }

}
