package com.starboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    private final WebClient githubWebClient;

    @Value("${github.oauth.client-id}")
    private String clientId;

    @Value("${github.oauth.client-secret}")
    private String clientSecret;

    public String getOAuthLoginUrl() {
        return "https://github.com/login/oauth/authorize" +
                "?client_id=" + clientId +
                "&scope=user:email,public_repo" +
                "&allow_signup=true";
    }

    public String exchangeCodeForToken(String code) {
        Map<?, ?> response = WebClient.builder()
                .baseUrl("https://github.com")
                .build()
                .post()
                .uri("/login/oauth/access_token")
                .header("Accept", "application/json")
                .bodyValue(Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "code", code
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.containsKey("error")) {
            throw new RuntimeException("GitHub OAuth error: " + (response != null ? response.get("error_description") : "null response"));
        }

        return (String) response.get("access_token");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserInfo(String accessToken) {
        return (Map<String, Object>) githubWebClient.get()
                .uri("/user")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRepoInfo(String fullName, String accessToken) {
        return (Map<String, Object>) githubWebClient.get()
                .uri("/repos/" + fullName)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public void starRepo(String owner, String repo, String accessToken) {
        githubWebClient.put()
                .uri("/user/starred/" + owner + "/" + repo)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Length", "0")
                .retrieve()
                .toBodilessEntity()
                .block();
        log.info("Starred repo: {}/{}", owner, repo);
    }

    public void unstarRepo(String owner, String repo, String accessToken) {
        githubWebClient.delete()
                .uri("/user/starred/" + owner + "/" + repo)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .block();
        log.info("Unstarred repo: {}/{}", owner, repo);
    }

    /**
     * Kiểm tra user có star repo này trên GitHub không.
     * - 204 → true (đang star)
     * - 404 → false (chưa/đã unstar)
     * - 401/403/5xx → throw RuntimeException (không rollback nhầm khi token lỗi / rate limit)
     */
    public boolean checkIfStarred(String owner, String repo, String accessToken) {
        Boolean result = githubWebClient.get()
                .uri("/user/starred/" + owner + "/" + repo)
                .header("Authorization", "Bearer " + accessToken)
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().thenReturn(true);
                    } else if (status == 404) {
                        return response.releaseBody().thenReturn(false);
                    } else {
                        return response.releaseBody()
                                .then(Mono.error(new RuntimeException(
                                        "GitHub API lỗi khi check star: HTTP " + status)));
                    }
                })
                .block();
        return Boolean.TRUE.equals(result);
    }
    /**
     * Lấy tất cả public repo của user (có phân trang, tối đa 100/lần)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMyRepos(String accessToken) {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;
        while (true) {
            int finalPage = page;
            List<Map<String, Object>> batch = (List<Map<String, Object>>) githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("type", "owner")
                            .queryParam("per_page", 100)
                            .queryParam("page", finalPage)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(List.class);
                        } else {
                            return response.releaseBody()
                                    .then(Mono.error(new RuntimeException(
                                            "GitHub API lỗi khi lấy repos: HTTP " + response.statusCode().value())));
                        }
                    })
                    .block();
            if (batch == null || batch.isEmpty()) break;
            all.addAll(batch);
            if (batch.size() < 100) break; // hết trang
            page++;
        }
        log.info("getMyRepos: fetched {} repos from GitHub", all.size());
        return all;
    }
}
