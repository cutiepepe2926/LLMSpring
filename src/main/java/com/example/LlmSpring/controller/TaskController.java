package com.example.LlmSpring.controller;

import com.example.LlmSpring.project.ProjectAccessService;
import com.example.LlmSpring.task.TaskLogVO;
import com.example.LlmSpring.task.request.TaskRequestDTO;
import com.example.LlmSpring.task.response.TaskResponseDTO;
import com.example.LlmSpring.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectAccessService projectAccessService;

    // --- [웹소켓 공통 전송 메소드] ---
    private void broadcastUpdate(Long taskId, String type, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("data", data);
        messagingTemplate.convertAndSend("/sub/tasks/" + taskId, message);
    }

    @GetMapping("/my-role")
    public ResponseEntity<Map<String, String>> getMyRole(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId) {
        projectAccessService.validateReadAccess(projectId, userId);
        String role = taskService.getMyRole(projectId, userId);
        return ResponseEntity.ok(Map.of("role", role));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createTask(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @RequestBody TaskRequestDTO requestDTO) {
        projectAccessService.validateWriteAccess(projectId, userId);
        taskService.createTask(projectId, userId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Created"));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponseDTO>> getTaskList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId) {
        projectAccessService.validateReadAccess(projectId, userId);
        return ResponseEntity.ok(taskService.getTaskList(projectId));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> getTaskDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        projectAccessService.validateReadAccess(projectId, userId);
        return ResponseEntity.ok(taskService.getTaskDetail(taskId));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        projectAccessService.validateWriteAccess(projectId, userId);

        String newStatus = body.get("status");
        taskService.updateStatus(taskId, userId, newStatus);

        // [실시간] 상태 변경 전송 & 로그 갱신
        broadcastUpdate(taskId, "STATUS", newStatus);
        broadcastUpdate(taskId, "LOG", taskService.getLogs(taskId));

        return ResponseEntity.ok(Map.of("message", "Status Updated"));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> updateTask(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody TaskRequestDTO requestDTO) {
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.updateTask(taskId, userId, requestDTO);

        broadcastUpdate(taskId, "TASK_UPDATE", taskService.getTaskDetail(taskId));
        broadcastUpdate(taskId, "LOG", taskService.getLogs(taskId));

        return ResponseEntity.ok(Map.of("message", "Updated"));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> deleteTask(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        projectAccessService.validateWriteAccess(projectId, userId);
        taskService.deleteTask(taskId, userId);

        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @GetMapping("/{taskId}/checklists")
    public List<com.example.LlmSpring.task.TaskCheckListVO> getCheckLists(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        projectAccessService.validateReadAccess(projectId, userId);
        return taskService.getCheckLists(taskId);
    }

    @PostMapping("/{taskId}/checklists")
    public ResponseEntity<Map<String, String>> addCheckList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.addCheckList(taskId, userId, body.get("content"));

        // [실시간] 체크리스트 목록 & 로그 갱신
        broadcastUpdate(taskId, "CHECKLIST", taskService.getCheckLists(taskId));
        broadcastUpdate(taskId, "LOG", taskService.getLogs(taskId));

        return ResponseEntity.ok(Map.of("message", "Checklist Added"));
    }

    @DeleteMapping("/{taskId}/checklists/{checklistId}")
    public ResponseEntity<Map<String, String>> deleteCheckList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId) {
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.deleteCheckList(taskId, checklistId, userId);

        // [실시간] 체크리스트 목록 & 로그 갱신
        broadcastUpdate(taskId, "CHECKLIST", taskService.getCheckLists(taskId));
        broadcastUpdate(taskId, "LOG", taskService.getLogs(taskId));

        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PatchMapping("/{taskId}/checklists/{checklistId}")
    public ResponseEntity<Map<String, String>> toggleCheckList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId,
            @RequestBody Map<String, Boolean> body) {
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.toggleCheckList(checklistId, body.get("is_done"), taskId, userId);

        // [실시간] 체크리스트 목록 & 로그 갱신
        broadcastUpdate(taskId, "CHECKLIST", taskService.getCheckLists(taskId));
        broadcastUpdate(taskId, "LOG", taskService.getLogs(taskId));

        return ResponseEntity.ok(Map.of("message", "Toggled"));
    }

    @GetMapping("/{taskId}/chats")
    public List<Map<String, Object>> getChats(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        projectAccessService.validateReadAccess(projectId, userId);
        return taskService.getChats(taskId);
    }

    @PostMapping("/{taskId}/chats")
    public ResponseEntity<String> addChat(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        projectAccessService.validateWriteAccess(projectId, userId);

        String content = body.get("content");
        taskService.addChat(taskId, userId, content);

        // [수정] 실시간 전송 데이터에 이름과 이미지 경로 추가
        // taskService.getMyRole() 등을 호출할 때 사용하는 UserMapper 등을 통해 조회하거나,
        // 편의상 taskService에 getUserInfo 메소드가 있다고 가정하고 호출
        // (직접 구현 시 TaskService에 getUserProfile(userId) 메소드 추가 필요)

        Map<String, Object> userInfo = taskService.getUserProfileSimple(userId); // *TaskService에 이 메소드 추가 필요

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("userId", userId);
        chatData.put("content", content);
        chatData.put("taskId", taskId);
        chatData.put("name", userInfo.get("name"));       // 추가됨
        chatData.put("filePath", userInfo.get("filePath")); // 추가됨

        broadcastUpdate(taskId, "CHAT", chatData);

        return ResponseEntity.ok("Chat added");
    }

    @GetMapping("/{taskId}/logs")
    public ResponseEntity<List<TaskLogVO>> getLogs(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        projectAccessService.validateReadAccess(projectId, userId);
        return ResponseEntity.ok(taskService.getLogs(taskId));
    }
}