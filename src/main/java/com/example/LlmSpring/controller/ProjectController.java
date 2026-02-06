package com.example.LlmSpring.controller;

import com.example.LlmSpring.project.request.ProjectCreateRequestDTO;
import com.example.LlmSpring.project.ProjectService;
import com.example.LlmSpring.project.request.ProjectStatusRequestDTO;
import com.example.LlmSpring.project.request.ProjectUpdateRequestDTO;
import com.example.LlmSpring.project.response.ProjectDashboardResponseDTO;
import com.example.LlmSpring.project.response.ProjectDetailResponseDTO;
import com.example.LlmSpring.project.response.ProjectListResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 추가됨
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * [프로젝트 생성 API]
     * POST /api/projects
     */
    @PostMapping
    public ResponseEntity<?> createProject(
            @AuthenticationPrincipal String userId,
            @RequestBody ProjectCreateRequestDTO dto)
    {

        System.out.println(dto.toString());

        // 서비스 호출을 통해 프로젝트 생성 로직 실행
        int projectId = projectService.createProject(dto, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(projectId);
    }

    /**
     * [프로젝트 정보 수정 API]
     * PUT /api/projects/{projectId}
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<String> updateProject(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectUpdateRequestDTO dto) {

        try {
            int result = projectService.updateProject(projectId, userId, dto);

            if (result == 1) {
                return ResponseEntity.ok("프로젝트 수정이 완료되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("수정할 프로젝트를 찾을 수 없습니다.");
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 상태 변경 API]
     * PATCH /api/projects/{projectId}/status
     */
    @PatchMapping("/{projectId}/status")
    public ResponseEntity<String> updateProjectStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectStatusRequestDTO dto) {

        try {
            int result = projectService.updateProjectStatus(projectId, userId, dto.getStatus());
            if (result == 1) {
                String message = "DONE".equals(dto.getStatus())
                        ? "프로젝트가 완료 처리되어 이제 수정할 수 없습니다."
                        : "프로젝트가 다시 활성화되었습니다.";
                return ResponseEntity.ok(message);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("대상을 찾을 수 없습니다.");
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 삭제 API (Soft Delete)]
     * DELETE /api/projects/{projectId}
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<String> deleteProject(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId) {

        try {
            int result = projectService.deleteProject(projectId, userId);
            if (result == 1) {
                return ResponseEntity.ok("프로젝트가 삭제 대기 상태로 변경되었습니다. (7일 후 완전 삭제)");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("삭제할 프로젝트를 찾을 수 없거나 이미 삭제되었습니다.");
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [참여 중인 프로젝트 목록 조회 API]
     * GET /api/projects
     */
    @GetMapping
    public ResponseEntity<?> getProjectList(
            @AuthenticationPrincipal String userId,
            @RequestParam(value = "type", defaultValue = "active") String type) {

        List<ProjectListResponseDTO> list;

        // 전달된 type 파라미터에 따라 적절한 서비스 메서드 호출
        if ("done".equals(type)) {
            list = projectService.getDoneProjects(userId);
        } else if ("trash".equals(type)) {
            list = projectService.getTrashProjects(userId);
        } else {
            list = projectService.getActiveProjects(userId);
        }

        return ResponseEntity.ok(list);
    }

    /**
     * [단일 프로젝트 상세 정보 조회 API]
     * GET /api/projects/{projectId}
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<?> getProjectDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId) {

        try {
            ProjectDetailResponseDTO project = projectService.getProjectDetail(projectId, userId);
            return ResponseEntity.ok(project);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 복구 API]
     * POST /api/projects/{projectId}/restore
     */
    @PostMapping("/{projectId}/restore")
    public ResponseEntity<String> restoreProject(
            @AuthenticationPrincipal String userId, // [변경]
            @PathVariable("projectId") int projectId) {

        try {
            int result = projectService.restoreProject(projectId, userId);

            if (result == 1) {
                return ResponseEntity.ok("프로젝트가 성공적으로 복구되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("복구 처리 중 오류가 발생했습니다.");
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 대시보드 통계 조회 API]
     * GET /api/projects/{projectId}/dashboard
     */
    @GetMapping("/{projectId}/dashboard")
    public ResponseEntity<ProjectDashboardResponseDTO> getProjectDashboard(
            @AuthenticationPrincipal String userId, // [변경]
            @PathVariable("projectId") Long projectId) {

        ProjectDashboardResponseDTO stats = projectService.getProjectDashboardStats(projectId, userId);
        return ResponseEntity.ok(stats);
    }
}