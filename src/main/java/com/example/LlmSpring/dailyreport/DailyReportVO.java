package com.example.LlmSpring.dailyreport;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DailyReportVO {
    private Long reportId;
    private LocalDate reportDate;
    private String title;
    private String content;
    private String summary; //3줄 요약
    private Boolean original_content; //true: 초안, false: 수정됨
    private String status; //DRAFT(작성중), //PUBLISHED(발행됨)
    private Integer commitCount;
    private LocalDate updatedAt;
    private Boolean isPublished;
    private Long projectId;
    private String userId;
}
