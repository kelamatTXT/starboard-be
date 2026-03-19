package com.starboard.repository;

import com.starboard.entity.Repo;
import com.starboard.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoRepository extends JpaRepository<Repo, Long> {
    Optional<Repo> findByFullName(String fullName);
    Page<Repo> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Repo> findAllByAddedBy(User addedBy);
    List<Repo> findAllByOwnerIgnoreCase(String owner);
}
