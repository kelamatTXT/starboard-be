package com.starboard.repository;

import com.starboard.entity.Repo;
import com.starboard.entity.StarRecord;
import com.starboard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StarRecordRepository extends JpaRepository<StarRecord, Long> {
    Optional<StarRecord> findByUserAndRepo(User user, Repo repo);
    boolean existsByUserAndRepo(User user, Repo repo);
    long countByRepo(Repo repo);
    List<StarRecord> findByUser(User user);

    @Query("SELECT s FROM StarRecord s JOIN FETCH s.user JOIN FETCH s.repo")
    List<StarRecord> findAllWithUserAndRepo();

    void deleteByRepo(Repo repo);

    /**
     * Tìm tất cả star records cũ hơn thời điểm chỉ định (dùng cho kiểm tra đối ứng)
     */
    @Query("SELECT s FROM StarRecord s JOIN FETCH s.user JOIN FETCH s.repo WHERE s.starredAt < :cutoff")
    List<StarRecord> findOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Kiểm tra: user có star bất kỳ repo nào thuộc owner (login) nào đó không?
     * Dùng để xác nhận "user B đã star lại repo của user A chưa?"
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM StarRecord s WHERE s.user = :user AND LOWER(s.repo.owner) = LOWER(:ownerLogin)")
    boolean hasStarredAnyRepoOfOwner(@Param("user") User user, @Param("ownerLogin") String ownerLogin);
}

