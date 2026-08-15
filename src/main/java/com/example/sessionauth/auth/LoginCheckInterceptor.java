package com.example.sessionauth.auth;

import com.example.sessionauth.session.LoginSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginCheckInterceptor implements HandlerInterceptor {
    private final LoginSessionStore loginSessionStore;
    public LoginCheckInterceptor(LoginSessionStore loginSessionStore) {
        this.loginSessionStore = loginSessionStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;

        if (!(handlerMethod.hasMethodAnnotation(LoginRequired.class))) return true;

        if (loginSessionStore.current().isEmpty()) {
            throw new UnauthorizedException();
        }

        return true;
    }
}
