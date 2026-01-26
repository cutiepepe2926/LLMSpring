package com.example.LlmSpring.project;

import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectVO {

    private Integer projectId;           // project_id (PK)
    private String name;                // name
    private String description;         // description
    private LocalDateTime startDate;    // start_date
    private LocalDateTime endDate;      // end_date
    private String status;              // status (ACTIVE | DONE)
    private Integer progress;           // progress
    private String githubRepoUrl;       // github_repo_url
    private String githubDefaultBranch; // github_default_branch
    private String githubConnectedStatus; // github_connected_status
    private LocalTime dailyReportTime;  // daily_report_time (TIME 타입 매핑)
    private LocalDateTime deletedAt;    // deleted_at (삭제 여부 확인용)
    private LocalDateTime updatedAt;    // updated_at
}