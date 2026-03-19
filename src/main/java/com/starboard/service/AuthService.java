package com.starboard.service;

import com.starboard.dto.AuthResponse;
import com.starboard.entity.User;
import com.starboard.repository.UserRepository;
import com.starboard.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final GitHubService githubService;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse handleGitHubCallback(String code) {
        // 1. Đổi code lấy access_token
        String accessToken = githubService.exchangeCodeForToken(code);
        log.info("Got access token from GitHub");

        // 2. Lấy thông tin user từ GitHub
        Map<String, Object> userInfo = githubService.getUserInfo(accessToken);
        String githubId = String.valueOf(userInfo.get("id"));
        String login = (String) userInfo.get("login");
        String name = (String) userInfo.get("name");
        String avatarUrl = (String) userInfo.get("avatar_url");
        String email = (String) userInfo.get("email");

        // 3. Tạo hoặc update user trong DB
        User user = userRepository.findByGithubId(githubId).orElseGet(() -> User.builder().credits(0).build());
        user.setGithubId(githubId);
        user.setLogin(login);
        user.setName(name != null ? name : login);
        user.setAvatarUrl(avatarUrl);
        user.setEmail(email);
        user.setAccessToken(accessToken);
        // Giữ nguyên credits nếu là user cũ, user mới đã có default = 10
        if (user.getCredits() == null) user.setCredits(0);
        user = userRepository.save(user);
        log.info("Saved/updated user: {} (credits: {})", login, user.getCredits());

        // 4. Tạo JWT
        String jwt = jwtUtil.generateToken(login, user.getId());

        return AuthResponse.builder()
                .token(jwt)
                .tokenType("Bearer")
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .login(user.getLogin())
                        .name(user.getName())
                        .avatarUrl(user.getAvatarUrl())
                        .email(user.getEmail())
                        .credits(user.getCredits())
                        .build())
                .build();
    }
}
