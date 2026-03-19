package com.starboard.controller;

import com.starboard.dto.AddRepoRequest;
import com.starboard.dto.BulkStarResult;
import com.starboard.dto.RepoDto;
import com.starboard.entity.User;
import com.starboard.service.RepoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;

    @GetMapping
    public ResponseEntity<Page<RepoDto>> getRepos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.getRepos(page, size, currentUser));
    }

    @PostMapping
    public ResponseEntity<RepoDto> addRepo(
            @Valid @RequestBody AddRepoRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.addRepo(request, currentUser));
    }

    @PostMapping("/{id}/star")
    public ResponseEntity<RepoDto> starRepo(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.starRepo(id, currentUser));
    }

    @DeleteMapping("/{id}/star")
    public ResponseEntity<RepoDto> unstarRepo(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.unstarRepo(id, currentUser));
    }

    @GetMapping("/{id}/star-status")
    public ResponseEntity<Map<String, Boolean>> getStarStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.ok(Map.of("starred", false));
        }
        return ResponseEntity.ok(Map.of("starred", repoService.isStarred(id, currentUser)));
    }

    /** Star tất cả repo chưa star (không phải repo của chính user) */
    @PostMapping("/star-all")
    public ResponseEntity<BulkStarResult> starAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.starAll(currentUser));
    }

    /** Unstar tất cả repo đã star */
    @PostMapping("/unstar-all")
    public ResponseEntity<BulkStarResult> unstarAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.unstarAll(currentUser));
    }

    /** Import tất cả public repo của user từ GitHub vào hệ thống */
    @PostMapping("/import-mine")
    public ResponseEntity<BulkStarResult> importMine(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.importMyRepos(currentUser));
    }

    /** Xoá tất cả repo do user thêm vào */
    @DeleteMapping("/delete-mine")
    public ResponseEntity<BulkStarResult> deleteMine(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(repoService.deleteMyRepos(currentUser));
    }
}
