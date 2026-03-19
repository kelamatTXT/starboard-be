package com.starboard.controller;

import com.starboard.dto.AuthResponse;
import com.starboard.entity.User;
import com.starboard.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @Value("${github.oauth.client-id}")
    private String githubClientId;

    @GetMapping("/github/login")
    public ResponseEntity<Map<String, String>> getLoginUrl() {
        String url = "https://github.com/login/oauth/authorize" +
                "?client_id=" + githubClientId +
                "&scope=user:email,repo" +
                "&allow_signup=true";
        return ResponseEntity.ok(Map.of("loginUrl", url));
    }

    @GetMapping("/github/callback")
    public ResponseEntity<AuthResponse> callback(@RequestParam String code) {
        log.info("GitHub callback received with code: {}", code.substring(0, Math.min(8, code.length())) + "...");
        AuthResponse response = authService.handleGitHubCallback(code);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserDto> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(AuthResponse.UserDto.builder()
                .id(user.getId())
                .login(user.getLogin())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .email(user.getEmail())
                .credits(user.getCredits())
                .build());
    }
}
