package com.starboard.job;

import com.starboard.entity.Repo;
import com.starboard.entity.StarRecord;
import com.starboard.entity.User;
import com.starboard.repository.StarRecordRepository;
import com.starboard.repository.UserRepository;
import com.starboard.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Job định kỳ kiểm tra user đã unstar trực tiếp trên GitHub chưa.
 * Nếu phát hiện unstar → rollback credit, xóa star_record.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StarVerificationJob {

    private final StarRecordRepository starRecordRepository;
    private final UserRepository userRepository;
    private final GitHubService githubService;

    /**
     * Chạy mỗi giờ (mặc định). Có thể cấu hình qua star.verify.cron trong application.yml
     */
    @Scheduled(cron = "${star.verify.cron:0 0 * * * *}")
    @Transactional
    public void verifyStar() {
        log.info("=== [StarVerificationJob] Bắt đầu kiểm tra star status từ GitHub...");
        List<StarRecord> records = starRecordRepository.findAllWithUserAndRepo();
        log.info("[StarVerificationJob] Tổng số star records cần kiểm tra: {}", records.size());

        int revokedCount = 0;
        for (StarRecord record : records) {
            User starer = record.getUser();
            Repo repo = record.getRepo();

            if (starer.getAccessToken() == null) continue;

            try {
                boolean stillStarred = githubService.checkIfStarred(
                        repo.getOwner(), repo.getName(), starer.getAccessToken()
                );

                if (!stillStarred) {
                    log.warn("[StarVerificationJob] Phát hiện unstar: user={} repo={} — rollback credit!",
                            starer.getLogin(), repo.getFullName());
                    rollbackCredit(record, starer, repo);
                    revokedCount++;
                }
            } catch (Exception e) {
                log.error("[StarVerificationJob] Lỗi kiểm tra {}/{}: {}",
                        starer.getLogin(), repo.getFullName(), e.getMessage());
            }
        }
        log.info("=== [StarVerificationJob] Hoàn thành. Đã rollback {} star record(s).", revokedCount);
    }

    private void rollbackCredit(StarRecord record, User starer, Repo repo) {
        // Trừ 1 credit người unstar qua @Modifying query — UPDATE thẳng vào DB
        int updated = userRepository.deductCredit(starer.getId());
        log.info("  -> deductCredit user={} id={}, rows updated={}", starer.getLogin(), starer.getId(), updated);

        // Tit-for-Tat: hoàn trả +1 credit cho owner repo (nếu có trong hệ thống)
        if (repo.getOwner() != null) {
            userRepository.findByLogin(repo.getOwner()).ifPresent(owner -> {
                int prev = owner.getCredits() != null ? owner.getCredits() : 0;
                owner.setCredits(prev + 1);
                userRepository.save(owner);
                log.info("  -> refundCredit owner={}, credits: {} -> {}", owner.getLogin(), prev, owner.getCredits());
            });
        }

        // Xóa star record
        starRecordRepository.delete(record);
        log.info("  -> Đã xóa star_record id={}", record.getId());
    }
}
