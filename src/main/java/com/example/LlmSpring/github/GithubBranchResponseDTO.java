package com.example.LlmSpring.github;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GithubBranchResponseDTO {
    private String name; // 브랜치 이름 (예: main)
    private String sha;  // 해당 브랜치의 최신 커밋 SHA (예: a1b2c3d...)
}