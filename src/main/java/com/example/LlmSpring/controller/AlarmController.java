package com.example.LlmSpring.controller;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.alarm.AlarmVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    // 1. 내 알림 목록 조회
    @GetMapping
    public ResponseEntity<List<AlarmVO>> getMyAlarms(@AuthenticationPrincipal String userId){

        List<AlarmVO> alarms = alarmService.getMyAlarms(userId);
        return ResponseEntity.ok(alarms);
    }

    // 2. 안 읽은 알림 개수 조회
    @GetMapping("/unread")
    public ResponseEntity<Integer> getUnreadCount(@AuthenticationPrincipal String userId){

        int count = alarmService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    // 3. 특정 알림 읽음 처리
    @PutMapping("/{alarmId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable int alarmId){
        alarmService.markAsRead(alarmId);
        return ResponseEntity.ok().build();
    }

    // 4. 모든 알림 읽음 처리
    @PutMapping("/read-all")
    public ResponseEntity<?> markAsReadAll(@AuthenticationPrincipal String userId){
        System.out.println("전부 읽음 컨트롤러 진입");

        alarmService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    // 5. 읽은 알림 삭제
    @DeleteMapping("/read")
    public ResponseEntity<?> deleteReadAlarms(@AuthenticationPrincipal String userId) {

        alarmService.deleteReadAlarms(userId);
        return ResponseEntity.ok().build();
    }

    // 6. 모든 알림 삭제
    @DeleteMapping("/all")
    public ResponseEntity<?> deleteAllAlarms(@AuthenticationPrincipal String userId) {

        alarmService.deleteAllAlarms(userId);
        return ResponseEntity.ok().build();
    }

    // 7. 알림 구독 (SSE)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal String userId){
        return alarmService.subscribe(userId);
    }
}