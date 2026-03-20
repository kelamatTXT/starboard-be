package com.starboard.job;

import com.starboard.entity.Repo;
import com.starboard.entity.StarRecord;
import com.starboard.entity.User;
import com.starboard.repository.StarRecordRepository;
import com.starboard.repository.UserRepository;
import com.starboard.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Job kiểm tra "star đối ứng":
 * 
 * Khi User A star repo của User B, User B phải star lại ít nhất 1 repo
 * của User A trong vòng N giờ. Nếu không → tự động unstar trên GitHub.
 *
 * Cấu hình:
 *   star.reciprocal.hours=24  (thời gian chờ đối ứng, mặc định 24h)
 *   star.reciprocal.cron      (lịch chạy, mặc định mỗi 30 phút)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReciprocalStarJob {

    private final StarRecordRepository starRecordRepository;
    private final UserRepository userRepository;
    private final GitHubService githubService;

    @Value("${star.reciprocal.hours:24}")
    private int reciprocalHours;

    /**
     * Chạy mỗi 30 phút (mặc định). Cấu hình: star.reciprocal.cron
     */
    @Scheduled(cron = "${star.reciprocal.cron:0 */30 * * * *}")
    @Transactional
    public void checkReciprocalStars() {
        log.info("=== [ReciprocalStarJob] Bắt đầu kiểm tra star đối ứng (timeout={}h)...", reciprocalHours);

        LocalDateTime cutoff = LocalDateTime.now().minusHours(reciprocalHours);
        List<StarRecord> oldRecords = starRecordRepository.findOlderThan(cutoff);
        log.info("[ReciprocalStarJob] Tìm thấy {} star records cũ hơn {}h", oldRecords.size(), reciprocalHours);

        int unstarredCount = 0;
        int skippedCount = 0;

        for (StarRecord record : oldRecords) {
            User starer = record.getUser();        // Người đã star
            Repo repo = record.getRepo();          // Repo được star
            String repoOwnerLogin = repo.getOwner(); // Chủ repo

            // Bỏ qua nếu chủ repo chính là người star (star repo của chính mình)
            if (starer.getLogin().equalsIgnoreCase(repoOwnerLogin)) {
                continue;
            }

            // Tìm user chủ repo trong hệ thống
            Optional<User> repoOwnerOpt = userRepository.findByLogin(repoOwnerLogin);
            if (repoOwnerOpt.isEmpty()) {
                // Chủ repo không có trong hệ thống → bỏ qua (không thể kiểm tra đối ứng)
                skippedCount++;
                continue;
            }

            User repoOwner = repoOwnerOpt.get();

            // Kiểm tra: Chủ repo (User B) đã star lại bất kỳ repo nào của người star (User A) chưa?
            boolean hasReciprocated = starRecordRepository.hasStarredAnyRepoOfOwner(repoOwner, starer.getLogin());

            if (!hasReciprocated) {
                // Chưa đối ứng → tự động unstar
                log.warn("[ReciprocalStarJob] ❌ KHÔNG đối ứng: {} star {}/{} nhưng {} chưa star lại → auto unstar!",
                        starer.getLogin(), repo.getOwner(), repo.getName(), repoOwnerLogin);

                try {
                    // Unstar trên GitHub
                    if (starer.getAccessToken() != null) {
                        githubService.unstarRepo(repo.getOwner(), repo.getName(), starer.getAccessToken());
                    }

                    // Rollback credit: trừ credit của người star
                    userRepository.deductCredit(starer.getId());

                    // Tit-for-Tat: hoàn trả +1 credit cho owner repo
                    int ownerPrev = repoOwner.getCredits() != null ? repoOwner.getCredits() : 0;
                    repoOwner.setCredits(ownerPrev + 1);
                    userRepository.save(repoOwner);

                    // Xóa star record
                    starRecordRepository.delete(record);

                    unstarredCount++;
                    log.info("  → Đã auto-unstar & rollback credit cho user={}, repo={}, owner refund={}->{}",
                            starer.getLogin(), repo.getFullName(), ownerPrev, repoOwner.getCredits());
                } catch (Exception e) {
                    log.error("  → Lỗi khi auto-unstar {}/{} cho user {}: {}",
                            repo.getOwner(), repo.getName(), starer.getLogin(), e.getMessage());
                }
            }
        }

        log.info("=== [ReciprocalStarJob] Hoàn thành: unstarred={}, skipped={} (chủ repo không trong hệ thống)",
                unstarredCount, skippedCount);
    }
}
