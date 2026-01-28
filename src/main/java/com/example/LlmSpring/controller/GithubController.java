package com.example.LlmSpring.controller;

import com.example.LlmSpring.github.GithubService;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/github")
@CrossOrigin(origins = "*")
public class GithubController {
    private final JWTService jwtService;
    private final GithubService githubService;

    @GetMapping("/{projectId}/getBranch")
    public ResponseEntity<?> getProjectBranch (@RequestHeader("Authorization") String authHeader,
                                               @PathVariable Long projectId)
    {
        // 1. 토큰 검증 및 사용자 ID 추출
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }
        try{
            List<String> branches = githubService.getProjectBranches(projectId, userId);
            return ResponseEntity.ok(branches);
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
