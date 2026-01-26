package com.example.LlmSpring.project;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class ProjectUpdateDTO {
    private String name;
    private String description;
    private String gitUrl;
    private LocalDateTime endDate;   // 마감일만 유지
    private LocalTime reportTime;
}