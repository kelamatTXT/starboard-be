package com.starboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "repos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String fullName;

    @Column(columnDefinition = "CLOB")
    private String description;

    private String language;

    private Long stargazersCount;

    private String htmlUrl;

    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
