package com.example.sessionauth.controller;

import com.example.sessionauth.annotation.LoginUser;
import com.example.sessionauth.domain.LoginRequest;
import com.example.sessionauth.domain.SessionUser;
import com.example.sessionauth.domain.User;
import com.example.sessionauth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@Controller
public class AuthController {
    private AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Value("${INSTANCE_ID:local}")
    private String instanceId;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest req,
            HttpServletRequest request
    ){
        User user = authService.authenticate(req.username(), req.password());

        if (user == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("USER", user.getUsername());
        session.setAttribute("ROLE", user.getRole());

        return ResponseEntity.ok(Map.of("message", "login success", "username", user.getUsername()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@LoginUser SessionUser user){
        // servelet을 바로 받는 경우는 드뭄 다른방법 알아보기
        // arguments resolver, filter, interceptor, aop

        return ResponseEntity.ok(Map.of(
                "username", user.username(),
                "role", user.role(),
                "instanceId", instanceId
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request){
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(Map.of("message", "logout success"));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
