package com.starboard.repository;

import com.starboard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGithubId(String githubId);
    Optional<User> findByLogin(String login);

    /** Trừ 1 credit (không để âm dưới 0) — UPDATE trực tiếp vào DB tránh JPA cache issues */
    @Modifying
    @Query("UPDATE User u SET u.credits = CASE WHEN u.credits > 0 THEN u.credits - 1 ELSE 0 END WHERE u.id = :userId")
    int deductCredit(@Param("userId") Long userId);
}
