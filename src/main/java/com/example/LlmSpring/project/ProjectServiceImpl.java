package com.example.LlmSpring.project;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("projectService")
@RequiredArgsConstructor // @Autowired 대신 권장하는 final + @RequiredArgsConstructor 방식
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class) // 예외 발생 시 롤백
    public int createProject(ProjectCreateDTO dto) { // 프로젝트 생성

        // 1. DTO 데이터를 ProjectVO로 변환 및 프로젝트 저장
        ProjectVO project = ProjectVO.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .startDate(LocalDateTime.now())        // 시작일을 현재 시간으로 설정
                .endDate(dto.getEndDate())
                .githubRepoUrl(dto.getGitUrl())
                .dailyReportTime(dto.getReportTime())
                .build();

        projectMapper.insertProject(project); // 만약에 DB 처리 실패 시 자동 롤백 예외처리

        // 2. 프로젝트가 성공적으로 생성되었고, 초대할 멤버가 있다면 등록
        if (dto.getMembers() != null && !dto.getMembers().isEmpty()) {
            projectMapper.insertProjectMembers(project.getProjectId(), dto.getMembers());
        }

        return project.getProjectId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProject(int projectId, ProjectUpdateDTO dto) { // 프로젝트 내용 수정
        // 1. DTO 데이터를 VO 객체로 변환 (startDate 제외)
        ProjectVO project = ProjectVO.builder()
                .projectId(projectId)
                .name(dto.getName())
                .description(dto.getDescription())
                .githubRepoUrl(dto.getGitUrl())
                .endDate(dto.getEndDate())     // 마감일만 반영
                .dailyReportTime(dto.getReportTime())
                .build();

        // 2. 수정 실행
        return projectMapper.updateProject(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProjectStatus(int projectId, String status) { // 프로젝트 ACTIVE <-> DONE(아카이브) 상태 변경
        return projectMapper.updateProjectStatus(projectId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProject(int projectId) { // 프로젝트 soft_delete
        // 현재 시간으로부터 7일 뒤의 시간을 계산하고 DB에 업데이트
        LocalDateTime deleteDate = LocalDateTime.now().plusDays(7);

        return projectMapper.deleteProject(projectId, deleteDate);
    }

    @Override
    public List<ProjectVO> getActiveProjects(String userId) { // 사용자가 참여중인 ACTIVE 상태의 프로젝트 목록 조회
        return projectMapper.getActiveProjectList(userId);
    }

    @Override
    public List<ProjectVO> getDoneProjects(String userId) { // 사용자가 참여중인 DONE 상태의 프로젝트 목록 조회
        return projectMapper.getDoneProjectList(userId);
    }

    @Override
    public List<ProjectVO> getTrashProjects(String userId) { // 사용자가 참여중인 삭제 예정의 프로젝트 목록 조회
        return projectMapper.getTrashProjectList(userId);
    }

}
