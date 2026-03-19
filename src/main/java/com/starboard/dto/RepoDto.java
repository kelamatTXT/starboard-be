package com.starboard.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RepoDto {
    private Long id;
    private String owner;
    private String name;
    private String fullName;
    private String description;
    private String language;
    private Long stargazersCount;
    private Long communityStars;
    private String htmlUrl;
    private String avatarUrl;
    private boolean starred;
    private boolean canStar;  // false nếu owner hết credit
    private LocalDateTime createdAt;
}
