package com.example.LlmSpring.controller;

import com.example.LlmSpring.dailyreport.DailyReportService;
import com.example.LlmSpring.dailyreport.response.DailyReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
@CrossOrigin(origins = "*")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    //1. 리포트 작성 페이지 진입
    @PostMapping("/today")
    public DailyReportResponseDTO createOrGetTodayReport(@PathVariable Long projectId) {
        String userId = "user1"; //실제 로그인 유저 ID 필요
        return dailyReportService.getOrCreateTodayReport(projectId, userId);
    }

    //2. 리포트 상세 조회
    @GetMapping("/{reportId}")
    public DailyReportResponseDTO getReport (@PathVariable Long projectId, @PathVariable Long reportId){
        return dailyReportService.getReportDetail(reportId);
    }

    //3. 리포트 수정 (임시 저장)
    @PutMapping("/{reportId}")
    public void updateReport(@PathVariable Long reportId, @RequestBody Map<String, String> body) {
        dailyReportService.updateReport(reportId, body.get("content"), body.get("title"));
    }

    //4. 리포트 발행 (완료 처리)
    @PatchMapping("/{reportId}/publish")
    public void publishReport(@PathVariable Long reportId) {
        dailyReportService.publishReport(reportId);
    }

}
