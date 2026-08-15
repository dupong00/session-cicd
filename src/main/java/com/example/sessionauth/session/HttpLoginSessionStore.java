package com.example.sessionauth.session;

import com.example.sessionauth.domain.SessionUser;
import com.example.sessionauth.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HttpLoginSessionStore implements LoginSessionStore {
    private final HttpServletRequest request;

    public HttpLoginSessionStore(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void create(User user) {
        HttpSession old =  request.getSession(false);
        if (old != null) {
            old.invalidate();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("USER", user.getUsername());
        session.setAttribute("ROLE", user.getRole());
    }

    @Override
    public Optional<SessionUser> current() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        String username = (String) session.getAttribute("USER");
        if (username == null) {
            return Optional.empty();
        }

        String role = (String) session.getAttribute("ROLE");
        return Optional.of(new SessionUser(username, role));
    }

    @Override
    public void invalidate() {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
