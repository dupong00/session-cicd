package com.example.sessionauth.interceptor;

import com.example.sessionauth.annotation.LoginRequired;
import com.example.sessionauth.domain.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        if (!(handler instanceof HandlerMethod)) return true;

        HandlerMethod method = (HandlerMethod) handler;
        if (!method.hasMethodAnnotation(LoginRequired.class)) return true;

        HttpSession session = request.getSession(false);


        if (session == null || session.getAttribute("USER") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        else {
            String username = (String) session.getAttribute("USER");
            String role = (String) session.getAttribute("ROLE");

            SessionUser sessionUser = new SessionUser(username, role);
            request.setAttribute("loginUser", sessionUser);
            return true;
        }
    }
}
