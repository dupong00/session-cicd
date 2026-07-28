package com.example.sessionauth.filter;

import com.example.sessionauth.domain.SessionUser;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;

import java.io.IOException;

public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("USER") == null) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"error\":\"not authenticated\"}");
        }

        else {
            String username = (String) session.getAttribute("USER");
            String role = (String) session.getAttribute("ROLE");
            SessionUser user = new SessionUser(username, role);

            req.setAttribute("loginUser", user);
            chain.doFilter(request, response);
        }
    }
}
