package com.example.LlmSpring.project.request;

import java.time.LocalDateTime;
import lombok.Data;
import java.time.LocalTime;
import java.util.List;

// 클라이언트의 요청 데이터를 담는 객체

@Data
public class ProjectCreateRequestDTO {
    private String name;            // name 매핑
    private String description;     // description 매핑
    private String gitUrl;        // github_repo_url 매핑
    private LocalTime reportTime;   // daily_report_time 매핑
    private LocalDateTime endDate; // end_date 매핑 (마감일)
    private List<String> members;   // 초대할 user_id 리스트 (프로젝트 멤버는 여러 명일 수도 있어서러 List 처리)
}