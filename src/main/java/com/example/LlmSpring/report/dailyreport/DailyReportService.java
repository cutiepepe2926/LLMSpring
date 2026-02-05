package com.example.LlmSpring.report.dailyreport;

import com.example.LlmSpring.report.dailyreport.response.DailyReportResponseDTO;
import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.util.EncryptionUtil;
import com.example.LlmSpring.util.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final DailyReportMapper dailyReportMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final EncryptionUtil encryptionUtil;
    private final S3Service s3Service;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    //1. 리포트 진입 (있으면 조회, 없으면 빈 객체 반환)
    @Transactional
    public DailyReportResponseDTO getOrCreateTodayReport(Long projectId, String userId){
        String today = LocalDate.now().toString();

        DailyReportVO existingReport = dailyReportMapper.selectReportByDate(projectId, userId, today);

        if (existingReport != null){
            return getReportDetail(existingReport.getReportId());
        }

        // 없으면 빈 DTO 반환 (프론트엔드에서 작성 모드 진입)
        DailyReportResponseDTO emptyDTO = new DailyReportResponseDTO(new DailyReportVO(), "Unknown");
        emptyDTO.setContent("# 오늘의 업무\n\n(우측 상단의 'Git 분석' 버튼을 눌러보세요!)");
        emptyDTO.setReportDate(today);
        emptyDTO.setReportId(null); // ID null -> 신규 작성

        return emptyDTO;
    }

    // [추가] Git 분석 (저장 없이 내용만 반환)
    public String analyzeGitCommits(Long projectId, String userId, String date) {
        try {
            GeneratedContent result = getGeneratedContentFromGithub(projectId, userId);
            return result.content;
        } catch (Exception e) {
            log.error("Git 분석 실패", e);
            return "# 분석 실패\n\n오류가 발생했습니다: " + e.getMessage();
        }
    }

    //2. 리포트 상세 조회
    public DailyReportResponseDTO getReportDetail(Long reportId) {
        DailyReportVO vo = dailyReportMapper.selectReportById(reportId);
        if (vo == null) throw new IllegalArgumentException("Report not found");

        DailyReportResponseDTO dto = convertToDTO(vo);

        // S3 URL에서 실제 텍스트 다운로드
        String textContent = fetchContentFromS3(vo.getContent());
        dto.setContent(textContent);

        List<DailyReportChatLogVO> chatLogs = dailyReportMapper.selectChatLogs(reportId);
        dto.setChatLogs(chatLogs);
        return dto;
    }

    //3. 리포트 수정 (S3 업로드 + DB 업데이트)
    @Transactional
    public void updateReport(Long reportId, String content, String title) {
        DailyReportVO existingVO = dailyReportMapper.selectReportById(reportId);
        if(existingVO == null) throw new IllegalArgumentException("Report not found");

        // S3 파일명 생성 (덮어쓰기)
        String dateStr = existingVO.getReportDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String s3Key = String.format("dailyReport/%d/%s_%s.md",
                existingVO.getProjectId(), dateStr, existingVO.getUserId());

        // S3 업로드
        String s3Url = s3Service.uploadTextContent(s3Key, content);

        // DB 업데이트
        existingVO.setTitle(title);
        existingVO.setContent(s3Url); // URL 저장
        existingVO.setDrFilePath(s3Url);
        existingVO.setOriginalContent(false);

        dailyReportMapper.updateReport(existingVO);
    }

    // [추가] 3-1. 리포트 신규 생성 (수동 저장)
    @Transactional
    public void createReportManual(Long projectId, String userId, String dateStr, String content) {
        LocalDate reportDate = LocalDate.parse(dateStr);
        String formattedDate = reportDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String s3Key = String.format("dailyReport/%d/%s_%s.md", projectId, formattedDate, userId);

        String s3Url = s3Service.uploadTextContent(s3Key, content);

        DailyReportVO newReport = new DailyReportVO();
        newReport.setProjectId(projectId);
        newReport.setUserId(userId);
        newReport.setReportDate(reportDate);
        newReport.setTitle(reportDate + " 리포트");
        newReport.setContent(s3Url);
        newReport.setDrFilePath(s3Url);
        newReport.setStatus("DRAFT");
        newReport.setCommitCount(0);
        newReport.setOriginalContent(false);

        dailyReportMapper.insertReport(newReport);
    }

    //4. 리포트 발행
    public void publishReport(Long reportId) {
        dailyReportMapper.updateReportPublishStatus(reportId, "PUBLISHED");
    }

    //5. 일일 리포트 요약 목록 조회
    public List<DailyReportResponseDTO> getDailyReportsByDate(Long projectId, String date) {
        List<DailyReportVO> reports = dailyReportMapper.selectReportsByDate(projectId, date);
        return reports.stream().map(vo -> {
            DailyReportResponseDTO dto = convertToDTO(vo);
            // 목록 조회 시 미리보기가 필요하다면 S3 다운로드 (성능 고려 필요)
            // dto.setContent(fetchContentFromS3(vo.getContent()));
            return dto;
        }).collect(Collectors.toList());
    }

    //6. 프로젝트 기여도 통계 조회
    public Map<String, Object> getProjectStats(Long projectId, String period) {
        return dailyReportMapper.selectProjectStats(projectId, period);
    }

    //7. 리포트 수동 재생성
    @Transactional
    public DailyReportResponseDTO regenerateReport(Long reportId) {
        DailyReportVO existingVO = dailyReportMapper.selectReportById(reportId);
        if (existingVO == null) throw new IllegalArgumentException("Report not found");

        GeneratedContent generated = getGeneratedContentFromGithub(existingVO.getProjectId(), existingVO.getUserId());

        // S3 덮어쓰기
        String s3Url = s3Service.uploadTextContent(existingVO.getDrFilePath(), generated.content);

        existingVO.setCommitCount(generated.commitCount);
        existingVO.setContent(s3Url);
        existingVO.setOriginalContent(true);

        dailyReportMapper.updateReport(existingVO);

        return getReportDetail(reportId);
    }

    //8. AI 채팅 기록 조회
    public List<Map<String, Object>> getChatLogs(Long reportId, int page, int size) {
        List<DailyReportChatLogVO> logs = dailyReportMapper.selectChatLogsPaging(reportId, page * size, size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DailyReportChatLogVO log : logs) {
            Map<String, Object> map = new HashMap<>();
            map.put("role", log.getRole());
            map.put("message", log.getMessage());
            result.add(map);
        }
        return result;
    }

    //9. AI 채팅 전송
    @Transactional
    public Map<String, Object> sendChatToAI(Long reportId, String message, String currentContent) {
        DailyReportChatLogVO userLog = new DailyReportChatLogVO();
        userLog.setReportId(reportId);
        userLog.setRole(true);
        userLog.setMessage(message);
        dailyReportMapper.insertChatLog(userLog);

        String prompt = String.format("""
            당신은 개발자의 일일 리포트 작성을 돕는 AI 조수입니다.
            [현재 리포트 내용]
            %s
            [사용자 요청]
            %s
            요청에 맞춰 답변해주세요.
            """, currentContent, message);

        String aiReplyText = callGeminiApi(prompt);

        DailyReportChatLogVO aiLog = new DailyReportChatLogVO();
        aiLog.setReportId(reportId);
        aiLog.setRole(false);
        aiLog.setMessage(aiReplyText);
        aiLog.setIsApplied(false);
        dailyReportMapper.insertChatLog(aiLog);

        Map<String, Object> response = new HashMap<>();
        response.put("reply", aiReplyText);
        return response;
    }

    //10. AI 제안 적용 로그 저장
    public void saveSuggestionLog(Long reportId, String suggestion, boolean isApplied) {
        DailyReportChatLogVO log = new DailyReportChatLogVO();
        log.setReportId(reportId);
        log.setRole(false);
        log.setSuggestionContent(suggestion);
        log.setIsApplied(isApplied);
        dailyReportMapper.insertChatLog(log);
    }

    //11. 리포트 설정 조회
    public Map<String, Object> getReportSettings(Long projectId) {
        return dailyReportMapper.selectReportSettings(projectId);
    }

    //12. 리포트 설정 변경
    public void updateReportSettings(Long projectId, Map<String, Object> settings) {
        dailyReportMapper.updateReportSettings(projectId, settings);
    }

    //VO -> DTO 변환
    private DailyReportResponseDTO convertToDTO(DailyReportVO vo){
        String userName = dailyReportMapper.selectUserName(vo.getUserId());
        return new DailyReportResponseDTO(vo, userName);
    }

    // [추가] S3 텍스트 다운로드 헬퍼
    private String fetchContentFromS3(String url) {
        if (url == null || !url.startsWith("http")) return url;
        try {
            RestTemplate restTemplate = new RestTemplate();
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes != null) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return "";
        } catch (Exception e) {
            log.error("S3 리포트 다운로드 실패: {}", url);
            return "# 로드 실패\n내용을 불러올 수 없습니다.";
        }
    }

    // --- [ GitHub & GEMINI Methods ] ---

    // 내부 데이터 전달용 클래스
    private static class GeneratedContent {
        String content;
        int commitCount;
        public GeneratedContent(String content, int commitCount) {
            this.content = content;
            this.commitCount = commitCount;
        }
    }

    private GeneratedContent getGeneratedContentFromGithub(Long projectId, String userId) {
        String aiContent = "금일 진행한 업무 내용을 작성해주세요.";
        int commitCount = 0;

        try {
            UserVO user = userMapper.getUserInfo(userId);
            ProjectVO project = projectMapper.selectProjectById(projectId);

            if (user != null && project != null && user.getGithubToken() != null && project.getGithubRepoUrl() != null) {
                String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
                String realGithubUsername = fetchGithubUsername(decryptedToken);

                if (realGithubUsername != null) {
                    List<Map<String, Object>> commits = fetchAllMyRecentCommits(
                            project.getGithubRepoUrl(), realGithubUsername, decryptedToken
                    );
                    commitCount = commits.size();

                    if (!commits.isEmpty()) {
                        aiContent = generateAiSummary(commits);
                    } else {
                        aiContent = "### 🚫 금일 커밋 내역 없음\n- '" + realGithubUsername + "' 계정으로 조회된 최근 24시간 커밋이 없습니다.";
                    }
                }
            }
        } catch (Exception e) {
            log.error("AI 리포트 생성 실패", e);
            aiContent = "AI 자동 생성에 실패했습니다. (오류: " + e.getMessage() + ")";
        }
        return new GeneratedContent(aiContent, commitCount);
    }

    private String fetchGithubUsername(String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.github.com/user",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("login")) {
                return (String) body.get("login");
            }
        } catch (Exception e) {
            log.error("GitHub 사용자 조회 실패", e);
        }
        return null;
    }

    private List<Map<String, Object>> fetchAllMyRecentCommits(String repoUrl, String githubId, String token) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        if (parts.length < 2) return Collections.emptyList();

        String owner = parts[parts.length - 2];
        String repo = parts[parts.length - 1];

        ZonedDateTime nowKST = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        ZonedDateTime sinceKST = nowKST.minusHours(24);
        String since = sinceKST.withZoneSameInstant(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        List<String> branches = new ArrayList<>();
        try {
            String branchesUrl = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
            ResponseEntity<List> response = restTemplate.exchange(branchesUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> branchList = response.getBody();
            if (branchList != null) {
                for (Map<String, Object> b : branchList) {
                    branches.add((String) b.get("name"));
                }
            }
        } catch (Exception e) {
            log.error("브랜치 목록 조회 실패", e);
            branches.add("main");
        }

        Map<String, Map<String, Object>> uniqueCommitsMap = new HashMap<>();
        Map<String, Set<String>> shaToBranches = new HashMap<>();

        for (String branch : branches) {
            try {
                String commitsUrl = String.format(
                        "https://api.github.com/repos/%s/%s/commits?per_page=10&sha=%s&author=%s&since=%s",
                        owner, repo, branch, githubId, since
                );

                ResponseEntity<List> response = restTemplate.exchange(commitsUrl, HttpMethod.GET, entity, List.class);
                List<Map<String, Object>> branchCommits = response.getBody();

                if (branchCommits != null) {
                    for (Map<String, Object> commit : branchCommits) {
                        String sha = (String) commit.get("sha");
                        uniqueCommitsMap.putIfAbsent(sha, commit);
                        shaToBranches.computeIfAbsent(sha, k -> new HashSet<>()).add(branch);
                    }
                }
            } catch (Exception e) {
                log.warn("브랜치 커밋 조회 실패 (" + branch + "): " + e.getMessage());
            }
        }

        if (uniqueCommitsMap.isEmpty()) return Collections.emptyList();

        List<CompletableFuture<Map<String, Object>>> futures = uniqueCommitsMap.values().stream()
                .map(commitItem -> CompletableFuture.supplyAsync(() -> {
                    String sha = (String) commitItem.get("sha");
                    String detailUrl = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);
                    try {
                        Map<String, Object> detail = (Map<String, Object>) restTemplate.exchange(detailUrl, HttpMethod.GET, entity, Map.class).getBody();
                        if (detail != null) {
                            detail.put("related_branches", new ArrayList<>(shaToBranches.getOrDefault(sha, Collections.emptySet())));
                        }
                        return detail;
                    } catch (Exception e) {
                        return null;
                    }
                }, executorService).thenApply(this::filterForAI))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .sorted((c1, c2) -> {
                    String d1 = (String) c1.get("date");
                    String d2 = (String) c2.get("date");
                    return d1.compareTo(d2);
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> filterForAI(Map<String, Object> original) {
        if (original == null) return null;
        Map<String, Object> filtered = new HashMap<>();
        Map<String, Object> commitInfo = (Map<String, Object>) original.get("commit");
        Map<String, Object> authorInfo = (Map<String, Object>) commitInfo.get("author");

        filtered.put("date", authorInfo.get("date"));
        filtered.put("message", commitInfo.get("message"));
        filtered.put("branches", original.get("related_branches"));

        List<Map<String, Object>> files = (List<Map<String, Object>>) original.get("files");
        List<Map<String, String>> fileChanges = new ArrayList<>();

        if (files != null) {
            for (Map<String, Object> file : files) {
                Map<String, String> fileData = new HashMap<>();
                fileData.put("filename", (String) file.get("filename"));
                fileData.put("status", (String) file.get("status"));
                String patch = (String) file.get("patch");
                fileData.put("patch", patch != null ? patch : "(Binary or Large file)");
                fileChanges.add(fileData);
            }
        }
        filtered.put("changes", fileChanges);
        return filtered;
    }

    private String generateAiSummary(List<Map<String, Object>> commitData) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-latest:generateContent?key=" + geminiApiKey;
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonCommitData;
        try{
            jsonCommitData = objectMapper.writeValueAsString(commitData);
        }catch (Exception e){
            jsonCommitData = commitData.toString();
        }

        String prompt = """
            ## Role
            당신은 소프트웨어 개발 프로젝트의 변경 사항을 문서화하는 전문 테크니컬 라이터입니다.
            제공된 커밋 데이터(JSON)를 분석하여 팀 공유용 기술 리포트를 작성하십시오.
            JSON 데이터에는 각 커밋이 속한 브랜치 정보("branches")가 포함되어 있습니다.

            ## Constraints
            1. **Tone**: 본문은 건조하고 전문적인 문체를 사용하십시오. (해요체 금지, 하십시오체 또는 명사형 종결 사용)
            2. **Format**: Notion과 호환되는 Markdown 형식을 엄수하십시오.
            3. **Grouping**: **반드시 '브랜치(Branch)'를 기준으로 커밋 내용을 그룹화하여 작성하십시오.**
            4. **Fact-based**: 제공된 데이터에 없는 내용을 추론하거나 꾸며내지 마십시오.

            ## Output Structure
            리포트는 반드시 아래의 구조를 따라야 합니다.

            ### 1. 📅 커밋 타임라인
            - 전체 커밋을 시간순으로 나열한 요약 그래프입니다.
            - 포맷: `YYYY-MM-DD HH:mm` | `[BranchName]` | `커밋 메시지`

            ### 2. 🌿 브랜치별 상세 작업 내역
            작업된 브랜치 별로 섹션을 나누어 상세 내용을 기술하십시오.
            
            #### 📂 [브랜치 이름] (예: feature/login)
            **[Commit Hash 7자리] 커밋 메시지**
             - **변경 사항**: (코드의 핵심 변경 내용 요약)
             - **상세**: (추가/수정/삭제된 파일 및 로직 설명)

            ### 3. 📝 금일 작업 요약 (Executive Summary)
            - 전체 브랜치의 작업을 통합하여 비즈니스 관점에서 3~5문장으로 요약하십시오.
            - **반드시 "금일 작업 내용은..."으로 시작하십시오.**

            ## Input Data (JSON)
            """ + jsonCommitData;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.2);
        requestBody.put("contents", Collections.singletonList(content));
        requestBody.put("generationConfig", generationConfig);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(geminiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("candidates")) return "AI 응답 오류";
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates.isEmpty()) return "AI 분석 결과가 없습니다.";
            Map<String, Object> resContent = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> resParts = (List<Map<String, Object>>) resContent.get("parts");
            return (String) resParts.get(0).get("text");
        } catch (Exception e) {
            log.error("Gemini API Error", e);
            throw new RuntimeException("AI 분석 중 오류 발생");
        }
    }

    private String callGeminiApi(String prompt) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));
        requestBody.put("contents", Collections.singletonList(content));

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(geminiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> resContent = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> resParts = (List<Map<String, Object>>) resContent.get("parts");
                    return (String) resParts.get(0).get("text");
                }
            }
        } catch (Exception e) {
            log.error("Gemini API Error", e);
        }
        return "AI 응답 오류가 발생했습니다.";
    }
}