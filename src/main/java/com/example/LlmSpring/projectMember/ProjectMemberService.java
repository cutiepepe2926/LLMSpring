package com.example.LlmSpring.projectMember;

import com.example.LlmSpring.projectMember.request.ProjectMemberInviteRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRoleRequestDTO;
import com.example.LlmSpring.projectMember.response.ProjectMemberResponseDTO;
import java.util.List;

public interface ProjectMemberService {

    // 프로젝트 멤버 목록 조회
    List<ProjectMemberResponseDTO> getMemberList(int projectId, String userId);

    // 프로젝트 멤버 초대
    void inviteMember(int projectId, String inviterId, ProjectMemberInviteRequestDTO dto);

    // 프로젝트 멤버 역할 변경
    void updateMemberRole(int projectId, String requesterId, ProjectMemberRoleRequestDTO dto);
}
