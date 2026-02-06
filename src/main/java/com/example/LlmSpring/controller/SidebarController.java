package com.example.LlmSpring.controller;

import com.example.LlmSpring.sidebar.SidebarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SidebarController {

    private final SidebarService sidebarService;

    // 메인 사이드바 조회
    @GetMapping("/sidebar")
    public ResponseEntity<?> getMainSidebar(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(sidebarService.getMainSidebar(userId));
    }

    // 프로젝트 사이드바 조회
    @GetMapping("/projects/{projectId}/sidebar")
    public ResponseEntity<?> getProjectSidebar(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") Long projectId) {

        return ResponseEntity.ok(sidebarService.getProjectSidebar(projectId, userId));
    }

    // 즐겨찾기 등록/해제 (토글)
    @PostMapping("/projects/{projectId}/favorite")
    public ResponseEntity<?> toggleFavorite(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") Long projectId) {
        // 서비스 호출
        boolean isFavorite = sidebarService.toggleFavorite(projectId, userId);

        // 결과 리턴 (true면 등록됨, false면 해제됨)
        return ResponseEntity.ok(isFavorite);
    }
}