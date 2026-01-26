package com.example.LlmSpring.project;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 데이터 접근을 위한 인터페이스

@Mapper
public interface ProjectMapper {

    // 1. 프로젝트 기본 정보 삽입 (성공 아니면 에러라서 void 선언)
    void insertProject(ProjectVO project);

    // 1-2. 초기 멤버들 대량 삽입 (성공 아니면 에러라서 void 선언) (Batch Insert <- 일반 Insert보다 통신 비용 감소)
    void insertProjectMembers(@Param("projectId") Integer projectId,
                              @Param("memberIds") List<String> memberIds);

    // 2. 프로젝트 정보 수정 (영향을 받은 행의 수 반환)
    int updateProject(ProjectVO project);

    // 3. 프로젝트 상태 변경 (ACTIVE <-> DONE)
    int updateProjectStatus(@Param("projectId") int projectId, @Param("status") String status);

    // 4. 프로젝트 삭제 (Soft Delete: deleted_at 날짜 기록)
    int deleteProject(@Param("projectId") int projectId, @Param("deletedAt") LocalDateTime deletedAt);

    // 5. 사용자가 참여 중인 활성 프로젝트 목록 조회 (status = 'ACTIVE')
    List<ProjectVO> getActiveProjectList(@Param("userId") String userId);

    // 6. 사용자가 참여 중인 완료 프로젝트 목록 조회 (status = 'DONE')
    List<ProjectVO> getDoneProjectList(@Param("userId") String userId);

    // 7. 참여 중인 삭제 예정 프로젝트 목록 조회 (deleted_at IS NOT NULL)
    List<ProjectVO> getTrashProjectList(@Param("userId") String userId);
}
