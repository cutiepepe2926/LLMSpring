package com.example.LlmSpring.report.finalreport;

import com.example.LlmSpring.report.dailyreport.DailyReportVO;
import com.example.LlmSpring.util.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalReportServiceImpl implements FinalReportService {

    private final FinalReportMapper finalReportMapper;
    private final S3Service s3Service;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Override
    @Transactional
    public String getOrCreateFinalReport(Long projectId, String reportType, String userId) {
        // 1. DB에 이미 존재하는 최종 리포트가 있는지 확인
        FinalReportVO existingReport = finalReportMapper.selectFinalReportByProjectId(projectId);

        if (existingReport != null) {
            log.info("기존 최종 리포트 발견 (ID: {}). S3에서 내용 로드 중...", existingReport.getFinalReportId());
            // DB에 저장된 URL을 이용해 S3에서 실제 텍스트 다운로드 후 반환
            return fetchContentFromS3(existingReport.getContent());
        }

        // 2. 없으면 AI를 통해 새로 생성
        log.info("최종 리포트 신규 생성 시작 (Project: {}, Type: {})", projectId, reportType);

        // 2-1. 일일 리포트 데이터 수집 (여기서 DailyReport URL -> 텍스트 변환 수행됨)
        String aggregatedContent = collectAllDailyReports(projectId);

        // 2-2. 프롬프트 생성
        String prompt = createPromptByType(reportType, aggregatedContent);

        // 2-3. AI 생성 요청 (실제 리포트 내용)
        String generatedContent = callGemini(prompt);

        // 3. S3에 마크다운 파일로 저장
        String s3Key = String.format("finalReport/FinalReport_%d.md", projectId);
        String s3Url = s3Service.uploadTextContent(s3Key, generatedContent);
        log.info("S3 업로드 완료: {}", s3Url);

        // 4. DB에 저장 (Insert) - Content 컬럼에 'S3 URL' 저장
        FinalReportVO newReport = new FinalReportVO();
        newReport.setProjectId(projectId);
        newReport.setTitle(generateTitle(reportType));
        newReport.setContent(s3Url); // URL 저장
        newReport.setStatus("DRAFT");
        newReport.setCreatedBy(userId);

        finalReportMapper.insertFinalReport(newReport);
        log.info("최종 리포트 메타데이터 DB 저장 완료 (ID: {})", newReport.getFinalReportId());

        // 프론트엔드에는 URL이 아닌 '실제 텍스트 내용'을 반환하여 바로 보여줌
        return generatedContent;
    }

    private String fetchContentFromS3(String url) {
        if (url == null || !url.startsWith("http")) {
            return url; // URL이 아니면 그대로 반환 (하위 호환성)
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("S3 최종 리포트 다운로드 실패 (URL: {}): {}", url, e.getMessage());
            return "리포트 내용을 불러오는 데 실패했습니다.";
        }
    }

    private String collectAllDailyReports(Long projectId) {
        List<DailyReportVO> reports = finalReportMapper.selectAllReportsByProjectId(projectId);

        if (reports.isEmpty()) {
            return "작성된 일일 리포트가 없습니다.";
        }

        StringBuilder aggregatedContent = new StringBuilder();
        RestTemplate restTemplate = new RestTemplate();

        aggregatedContent.append(String.format("=== Project ID: %d Daily Reports ===\n\n", projectId));

        for (DailyReportVO report : reports) {
            String date = report.getReportDate().toString();
            String s3Url = report.getContent();

            aggregatedContent.append(String.format("## Date: %s\n", date));

            try {
                if (s3Url != null && s3Url.startsWith("http")) {
                    String textContent = restTemplate.getForObject(s3Url, String.class);
                    aggregatedContent.append(textContent).append("\n\n");
                } else {
                    // 예전 데이터 등 URL이 아닌 경우 텍스트 그대로 사용
                    aggregatedContent.append(s3Url).append("\n\n");
                }
            } catch (Exception e) {
                log.error("일일 리포트 로드 실패 (ID: {}): {}", report.getReportId(), e.getMessage());
                aggregatedContent.append("(내용 로드 실패)\n\n");
            }
        }

        return aggregatedContent.toString();
    }

    private String createPromptByType(String reportType, String aggregatedDailyReports) {
        String basePrompt = """
            ## Role
            당신은 IT 프로젝트의 결과물을 정리하는 전문 테크니컬 라이터이자 PM입니다.
            제공된 '일일 업무 리포트 모음'을 분석하여, 요청된 형식에 맞춰 최종 문서를 작성하십시오.
            
            ## Input Data (Daily Reports)
            """ + aggregatedDailyReports + "\n\n";

        String specificPrompt;

        switch (reportType) {
            case "PROJECT_REPORT":
                specificPrompt = """
                    ## Output Format: [프로젝트 결과 보고서]
                    다음 목차에 따라 마크다운 형식으로 작성하십시오.
                    
                    # [프로젝트 이름] 결과 보고서
                    ## 1. 프로젝트 개요
                    - 프로젝트 진행 기간 및 주요 목표, 전체 흐름 요약
                    ## 2. 주요 개발 내용
                    - 초기/중기/후기 단계별 주요 개발 사항 및 성과
                    ## 3. 이슈 및 해결 과정
                    - 주요 트러블슈팅 사례 (문제-원인-해결)
                    ## 4. 최종 회고
                    - 성과와 아쉬웠던 점, 향후 개선 방향
                    
                    ## Constraint
                    - 비즈니스 보고용 톤앤매너(하십시오체)를 유지하십시오.
                    """;
                break;

            case "PORTFOLIO":
                specificPrompt = """
                    ## Output Format: [개발자 포트폴리오]
                    채용 담당자에게 어필할 수 있는 포트폴리오 형식으로 작성하십시오.
                    
                    # [프로젝트 이름]
                    > 한 줄 소개
                    ## 🛠 Tech Stack & Tools
                    - 사용된 기술 스택 나열
                    ## 💡 Key Features
                    - 내가 기여한 핵심 기능 3~4가지 (문제 해결 관점)
                    ## 🚀 Trouble Shooting
                    - 가장 인상 깊은 문제 해결 경험 (STAR 기법)
                    ## 📈 Growth & Insight
                    - 기술적 성장 포인트
                    
                    ## Constraint
                    - '나' 주어 사용, 수치적 성과 강조.
                    """;
                break;

            case "TECHNICAL_DOC":
                specificPrompt = """
                    ## Output Format: [기술 명세서 (README)]
                    GitHub README 또는 Wiki용 기술 문서입니다.
                    
                    # [프로젝트 이름] Technical Documentation
                    ## 1. Architecture Overview
                    - 전체 구조 및 모듈 관계
                    ## 2. API & Data Flow
                    - 주요 기능의 데이터 흐름 및 로직 설명
                    ## 3. Detailed Implementation
                    - 주요 클래스, DB 스키마, 디자인 패턴 등 구현 상세
                    ## 4. Environment & Deployment
                    - 개발/배포 환경 설정
                    
                    ## Constraint
                    - 전문 용어 사용, 명확한 기술적 서술.
                    """;
                break;

            default:
                specificPrompt = "제공된 내용을 바탕으로 프로젝트 요약 보고서를 작성하십시오.";
        }

        return basePrompt + specificPrompt;
    }

    private String callGemini(String prompt) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();

        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.3);

        requestBody.put("contents", Collections.singletonList(content));
        requestBody.put("generationConfig", generationConfig);

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
            return "AI 응답을 받아오지 못했습니다.";

        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            return "최종 리포트 생성 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private String generateTitle(String reportType) {
        if ("PROJECT_REPORT".equals(reportType)) return "프로젝트 결과 보고서";
        if ("PORTFOLIO".equals(reportType)) return "개발자 포트폴리오";
        if ("TECHNICAL_DOC".equals(reportType)) return "기술 명세서";
        return "최종 리포트";
    }
}
