package com.starboard.service;

import com.starboard.dto.AddRepoRequest;
import com.starboard.dto.BulkStarResult;
import com.starboard.dto.RepoDto;
import com.starboard.entity.Repo;
import com.starboard.entity.StarRecord;
import com.starboard.entity.User;
import com.starboard.repository.RepoRepository;
import com.starboard.repository.StarRecordRepository;
import com.starboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoService {

    private final RepoRepository repoRepository;
    private final StarRecordRepository starRecordRepository;
    private final UserRepository userRepository;
    private final GitHubService githubService;

    public Page<RepoDto> getRepos(int page, int size, User currentUser) {
        return repoRepository.findAll(PageRequest.of(page, size))
                .map(repo -> toDto(repo, currentUser));
    }

    public RepoDto getRepo(Long id, User currentUser) {
        Repo repo = repoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Repo không tồn tại: " + id));
        return toDto(repo, currentUser);
    }

    @Transactional
    public RepoDto addRepo(AddRepoRequest request, User currentUser) {
        String fullName = request.getFullName().trim();
        String[] parts = fullName.split("/");
        if (parts.length != 2) {
            throw new RuntimeException("fullName phải có dạng 'owner/repo'");
        }

        Optional<Repo> existing = repoRepository.findByFullName(fullName);
        if (existing.isPresent()) {
            throw new RuntimeException("Repo '" + fullName + "' đã tồn tại trong hệ thống");
        }

        Map<String, Object> ghInfo = githubService.getRepoInfo(fullName, currentUser.getAccessToken());
        if (ghInfo == null) {
            throw new RuntimeException("Repo '" + fullName + "' không tồn tại trên GitHub hoặc bạn không có quyền truy cập");
        }

        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription()
                : (String) ghInfo.get("description");

        Repo repo = Repo.builder()
                .owner(parts[0])
                .name(parts[1])
                .fullName(fullName)
                .description(description)
                .language((String) ghInfo.get("language"))
                .stargazersCount(ghInfo.get("stargazers_count") instanceof Number
                        ? ((Number) ghInfo.get("stargazers_count")).longValue() : 0L)
                .htmlUrl((String) ghInfo.get("html_url"))
                .avatarUrl(ghInfo.get("owner") instanceof Map<?,?>
                        ? (String) ((Map<?,?>) ghInfo.get("owner")).get("avatar_url") : null)
                .addedBy(currentUser)
                .build();

        repoRepository.save(repo);
        return toDto(repo, currentUser);
    }

    @Transactional
    public RepoDto starRepo(Long repoId, User currentUser) {
        Repo repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repo không tồn tại: " + repoId));

        if (repo.getOwner() != null && repo.getOwner().equalsIgnoreCase(currentUser.getLogin())) {
            throw new RuntimeException("Bạn không thể star repo của chính mình trên GitHub");
        }

        if (starRecordRepository.existsByUserAndRepo(currentUser, repo)) {
            throw new RuntimeException("Bạn đã star repo này rồi");
        }

        githubService.starRepo(repo.getOwner(), repo.getName(), currentUser.getAccessToken());

        StarRecord record = StarRecord.builder()
                .user(currentUser)
                .repo(repo)
                .build();
        starRecordRepository.save(record);

        int prevCredits = currentUser.getCredits() != null ? currentUser.getCredits() : 0;
        currentUser.setCredits(prevCredits + 1);
        userRepository.save(currentUser);
        log.info("Starer {} credits: {} -> {}", currentUser.getLogin(), prevCredits, currentUser.getCredits());

        return toDto(repo, currentUser);
    }

    @Transactional
    public RepoDto unstarRepo(Long repoId, User currentUser) {
        Repo repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repo không tồn tại: " + repoId));

        StarRecord record = starRecordRepository.findByUserAndRepo(currentUser, repo)
                .orElseThrow(() -> new RuntimeException("Bạn chưa star repo này"));

        githubService.unstarRepo(repo.getOwner(), repo.getName(), currentUser.getAccessToken());

        starRecordRepository.delete(record);

        int prevCredits = currentUser.getCredits() != null ? currentUser.getCredits() : 0;
        currentUser.setCredits(Math.max(0, prevCredits - 1));
        userRepository.save(currentUser);
        log.info("Unstarer {} credits: {} -> {}", currentUser.getLogin(), prevCredits, currentUser.getCredits());

        return toDto(repo, currentUser);
    }

    public boolean isStarred(Long repoId, User currentUser) {
        Repo repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repo không tồn tại: " + repoId));
        return starRecordRepository.existsByUserAndRepo(currentUser, repo);
    }

    @Transactional
    public BulkStarResult starAll(User currentUser) {
        List<Repo> allRepos = repoRepository.findAll();
        int success = 0, failed = 0, skipped = 0;

        for (Repo repo : allRepos) {
            if (repo.getOwner() != null && repo.getOwner().equalsIgnoreCase(currentUser.getLogin())) {
                skipped++;
                continue;
            }
            if (starRecordRepository.existsByUserAndRepo(currentUser, repo)) {
                skipped++;
                continue;
            }
            try {
                githubService.starRepo(repo.getOwner(), repo.getName(), currentUser.getAccessToken());
                starRecordRepository.save(StarRecord.builder().user(currentUser).repo(repo).build());
                success++;
            } catch (Exception e) {
                log.error("starAll: Không thể star {}: {}", repo.getFullName(), e.getMessage());
                failed++;
            }
        }
        if (success > 0) {
            int prev = currentUser.getCredits() != null ? currentUser.getCredits() : 0;
            currentUser.setCredits(prev + success);
            userRepository.save(currentUser);
        }
        log.info("starAll: success={}, skipped={}, failed={}, +{}credits", success, skipped, failed, success);
        return BulkStarResult.builder()
                .success(success).failed(failed).skipped(skipped).creditsChanged(success).build();
    }

    @Transactional
    public BulkStarResult unstarAll(User currentUser) {
        List<StarRecord> records = starRecordRepository.findByUser(currentUser);
        int success = 0, failed = 0;

        for (StarRecord record : records) {
            Repo repo = record.getRepo();
            try {
                githubService.unstarRepo(repo.getOwner(), repo.getName(), currentUser.getAccessToken());
                starRecordRepository.delete(record);
                success++;
            } catch (Exception e) {
                log.error("unstarAll: Không thể unstar {}: {}", repo.getFullName(), e.getMessage());
                failed++;
            }
        }
        if (success > 0) {
            int prev = currentUser.getCredits() != null ? currentUser.getCredits() : 0;
            currentUser.setCredits(Math.max(0, prev - success));
            userRepository.save(currentUser);
        }
        log.info("unstarAll: success={}, failed={}, -{}credits", success, failed, success);
        return BulkStarResult.builder()
                .success(success).failed(failed).skipped(0).creditsChanged(-success).build();
    }

    @Transactional
    public BulkStarResult importMyRepos(User currentUser) {
        List<Map<String, Object>> ghRepos = githubService.getMyRepos(currentUser.getAccessToken());
        int success = 0, skipped = 0;

        for (Map<String, Object> ghRepo : ghRepos) {
            String fullName = (String) ghRepo.get("full_name");
            if (repoRepository.findByFullName(fullName).isPresent()) {
                skipped++;
                continue;
            }

            String description = (String) ghRepo.get("description");
            Repo repo = Repo.builder()
                    .owner((String) ((Map<?, ?>) ghRepo.get("owner")).get("login"))
                    .name((String) ghRepo.get("name"))
                    .fullName(fullName)
                    .description(description)
                    .language((String) ghRepo.get("language"))
                    .stargazersCount(ghRepo.get("stargazers_count") instanceof Number
                            ? ((Number) ghRepo.get("stargazers_count")).longValue() : 0L)
                    .htmlUrl((String) ghRepo.get("html_url"))
                    .avatarUrl((String) ((Map<?, ?>) ghRepo.get("owner")).get("avatar_url"))
                    .addedBy(currentUser)
                    .build();

            repoRepository.save(repo);
            success++;
        }
        
        log.info("importMyRepos: success={}, skipped={}", success, skipped);
        return BulkStarResult.builder()
                .success(success).failed(0).skipped(skipped).creditsChanged(0).build();
    }

    @Transactional
    public BulkStarResult deleteMyRepos(User currentUser) {
        // Chỉ xoá repo mà owner trên GitHub = login của user hiện tại
        List<Repo> myRepos = repoRepository.findAllByOwnerIgnoreCase(currentUser.getLogin());
        int deleted = 0;

        for (Repo repo : myRepos) {
            starRecordRepository.deleteByRepo(repo);
            repoRepository.delete(repo);
            deleted++;
        }

        log.info("deleteMyRepos: deleted={} repos (owner={}) for user={}", deleted, currentUser.getLogin(), currentUser.getId());
        return BulkStarResult.builder()
                .success(deleted).failed(0).skipped(0).creditsChanged(0).build();
    }

    private RepoDto toDto(Repo repo, User currentUser) {
        boolean starred = currentUser != null && starRecordRepository.existsByUserAndRepo(currentUser, repo);
        long communityStars = starRecordRepository.countByRepo(repo);

        boolean canStar = !starred;
        if (canStar && repo.getOwner() != null && currentUser != null) {
            canStar = !repo.getOwner().equalsIgnoreCase(currentUser.getLogin());
        }

        return RepoDto.builder()
                .id(repo.getId())
                .owner(repo.getOwner())
                .name(repo.getName())
                .fullName(repo.getFullName())
                .description(repo.getDescription())
                .language(repo.getLanguage())
                .stargazersCount(repo.getStargazersCount())
                .communityStars(communityStars)
                .htmlUrl(repo.getHtmlUrl())
                .avatarUrl(repo.getAvatarUrl())
                .starred(starred)
                .canStar(canStar)
                .createdAt(repo.getCreatedAt())
                .build();
    }
}
