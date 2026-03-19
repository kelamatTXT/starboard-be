package com.starboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String tokenType;
    private UserDto user;

    @Data
    @Builder
    public static class UserDto {
        private Long id;
        private String login;
        private String name;
        private String avatarUrl;
        private String email;
        private Integer credits;
    }
}
