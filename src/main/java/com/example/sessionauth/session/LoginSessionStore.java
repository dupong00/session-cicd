package com.example.sessionauth.session;

import com.example.sessionauth.domain.SessionUser;
import com.example.sessionauth.domain.User;

import java.util.Optional;

public interface LoginSessionStore {
    void create(User user);
    Optional<SessionUser> current();
    void invalidate();
}
