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

    public List<String> getProjectBranches(Long projectId, String userId){
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
        String[] parts = repoUrl.replace(".git", "").split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("잘못된 GitHub 저장소 URL입니다.");
        }
        String owner = parts[parts.length - 2];
        String repo = parts[parts.length - 1];

        // 6. GitHub API 호출
        return fetchBranchesFromGithub(owner, repo, decryptedToken);
    }

    private List<String> fetchBranchesFromGithub(String owner, String repo, String token) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String branchesUrl = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
        List<String> branchNames = new ArrayList<>();

        try {
            ResponseEntity<List> response = restTemplate.exchange(branchesUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> branchList = response.getBody();

            if (branchList != null) {
                for (Map<String, Object> branch : branchList) {
                    branchNames.add((String) branch.get("name"));
                }
            }
        } catch (Exception e) {
            log.error("GitHub 브랜치 조회 실패: {}", e.getMessage());
            throw new RuntimeException("GitHub API 호출 실패: " + e.getMessage());
        }

        return branchNames;
    }
}
