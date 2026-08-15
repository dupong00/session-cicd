package com.example.sessionauth.controller;

import com.example.sessionauth.auth.LoginRequired;
import com.example.sessionauth.auth.LoginUser;
import com.example.sessionauth.domain.LoginRequest;
import com.example.sessionauth.domain.MeResponse;
import com.example.sessionauth.domain.SessionUser;
import com.example.sessionauth.domain.User;
import com.example.sessionauth.service.AuthService;
import com.example.sessionauth.session.LoginSessionStore;
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
    private final AuthService authService;
    private final LoginSessionStore loginSessionStore;

    public AuthController(AuthService authService, LoginSessionStore loginSessionStore) {
        this.authService = authService;
        this.loginSessionStore = loginSessionStore;
    }

    @Value("${INSTANCE_ID:local}")
    private String instanceId;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req){

        User user = authService.authenticate(req.username(), req.password());

        if (user == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        loginSessionStore.create(user);

        return ResponseEntity.ok(Map.of("message", "login success", "username", user.getUsername()));
    }

    @LoginRequired
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@LoginUser SessionUser user){
        return ResponseEntity.ok(new MeResponse(user.username(), user.role(), instanceId));
    }

    @LoginRequired
    @PostMapping("/logout")
    public ResponseEntity<?> logout(){
        loginSessionStore.invalidate();
        return ResponseEntity.ok(Map.of("message", "logout success"));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
