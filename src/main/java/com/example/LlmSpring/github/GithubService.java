package com.example.LlmSpring.github;

import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import com.example.LlmSpring.projectMember.ProjectMemberMapper;
import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final EncryptionUtil encryptionUtil;

    public List<GithubBranchResponseDTO> getProjectBranches(Long projectId, String userId){
        // 1. 프로젝트 참여 멤버인지 확인
        if(!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)){
            throw new IllegalArgumentException("해당 프로젝트의 참여 멤버가 아닙니다.");
        }

        // 2. 프로젝트 정보 조회
        ProjectVO project = projectMapper.selectProjectById(projectId);
        if(project == null || project.getGithubRepoUrl() == null){
            throw new IllegalArgumentException("프로젝트가 존재하지 않거나 GitHub 저장소가 연결되지 않았습니다.");
        }

        // 3. 사용자 정보 조회
        UserVO user = userMapper.getUserInfo(userId);
        if (user == null || user.getGithubToken() == null) {
            throw new IllegalArgumentException("GitHub 계정이 연동되지 않은 사용자입니다.");
        }

        // 4. 토큰 복호화
        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());

        // 5. Repo URL 파싱 (Owner, Repo 이름 추출)
        String repoUrl = project.getGithubRepoUrl();
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl());

        // 6. GitHub API 호출
        return fetchBranchesFromGithub(repoInfo[0], repoInfo[1], decryptedToken);
    }

    private List<GithubBranchResponseDTO> fetchBranchesFromGithub(String owner, String repo, String token) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String branchesUrl = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
        List<GithubBranchResponseDTO> branchListResult = new ArrayList<>();

        try {
            ResponseEntity<List> response = restTemplate.exchange(branchesUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> responseList = response.getBody();

            if (responseList != null) {
                for (Map<String, Object> branchData : responseList) {
                    // 1. 브랜치 이름 추출
                    String name = (String) branchData.get("name");

                    // 2. 최신 커밋 SHA 추출
                    Map<String, Object> commitData = (Map<String, Object>) branchData.get("commit");
                    String sha = (commitData != null) ? (String) commitData.get("sha") : null;

                    // 3. DTO 생성 및 추가
                    branchListResult.add(GithubBranchResponseDTO.builder()
                            .name(name)
                            .sha(sha)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("GitHub 브랜치 조회 실패: {}", e.getMessage());
            throw new RuntimeException("GitHub API 호출 실패: " + e.getMessage());
        }

        return branchListResult;
    }

    public List<Map<String, Object>> getCommitsBySha(Long projectId, String userId, String sha){
        // 1. 권한 및 정보 검증 (공통 로직)
        if (!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)) {
            throw new IllegalArgumentException("해당 프로젝트의 참여 멤버가 아닙니다.");
        }

        ProjectVO project = projectMapper.selectProjectById(projectId);
        if (project == null || project.getGithubRepoUrl() == null) {
            throw new IllegalArgumentException("프로젝트가 존재하지 않거나 GitHub 저장소가 연결되지 않았습니다.");
        }

        UserVO user = userMapper.getUserInfo(userId);
        if (user == null || user.getGithubToken() == null) {
            throw new IllegalArgumentException("GitHub 계정이 연동되지 않은 사용자입니다.");
        }

        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl()); // [Owner, Repo]

        // 2. GitHub API 호출
        return fetchCommitsFromGithub(repoInfo[0], repoInfo[1], sha, decryptedToken);
    }

    private String[] parseRepoUrl(String repoUrl) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("잘못된 GitHub 저장소 URL입니다.");
        }
        return new String[]{parts[parts.length - 2], parts[parts.length - 1]};
    }


    private List<Map<String, Object>> fetchCommitsFromGithub(String owner, String repo, String sha, String token) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // sha 파라미터로 특정 지점부터의 커밋을 가져옴 (최근 30개)
        String commitsUrl = String.format("https://api.github.com/repos/%s/%s/commits?sha=%s&per_page=30", owner, repo, sha);
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            ResponseEntity<List> response = restTemplate.exchange(commitsUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> responseList = response.getBody();

            if (responseList != null) {
                for (Map<String, Object> commitItem : responseList) {
                    Map<String, Object> commitInfo = (Map<String, Object>) commitItem.get("commit");
                    Map<String, Object> authorInfo = (Map<String, Object>) commitInfo.get("author");

                    Map<String, Object> simplifiedCommit = new HashMap<>();
                    simplifiedCommit.put("sha", commitItem.get("sha"));
                    simplifiedCommit.put("message", commitInfo.get("message"));
                    simplifiedCommit.put("authorName", authorInfo.get("name"));
                    simplifiedCommit.put("date", authorInfo.get("date"));
                    simplifiedCommit.put("htmlUrl", commitItem.get("html_url"));

                    resultList.add(simplifiedCommit);
                }
            }
        } catch (Exception e) {
            log.error("GitHub 커밋 조회 실패: {}", e.getMessage());
            throw new RuntimeException("GitHub 커밋 로그 조회 실패: " + e.getMessage());
        }
        return resultList;
    }
}
