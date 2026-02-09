package com.example.LlmSpring.controller;

import com.example.LlmSpring.scheduler.ProjectScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TestSchedulerController {

    private final ProjectScheduler projectScheduler;

    // 스케줄러 테스트용 임시 컨트롤러 (지우지 마세용)
    @GetMapping("/api/test/scheduler")
    public String runSchedulerManually() {
        projectScheduler.runDailyProjectCheck();
        return "스케줄러 수동 실행 완료! 로그와 DB를 확인하세요.";
    }
}