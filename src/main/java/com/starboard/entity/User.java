package com.starboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String githubId;

    @Column(unique = true, nullable = false)
    private String login;

    private String name;

    private String email;

    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Builder.Default
    @Column(nullable = false)
    private Integer credits = 0; // Bắt đầu từ 0, phải star để kiếm credit

    @CreationTimestamp
    private LocalDateTime createdAt;
}
