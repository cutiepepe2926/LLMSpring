package com.example.LlmSpring.issue.chat;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueChatVO {
    private Integer chatId;      // PK
    private Integer issueId;     // FK
    private Integer projectId;   // FK (데이터 무결성 및 권한 체크용)
    private String userId;       // FK (작성자 ID)
    private String content;      // 메시지 내용
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; // 생성 시간

    // DB 테이블에는 없지만 화면 표시를 위해 Join해서 가져올 필드
    private String senderName;   // 작성자 이름 (User 테이블)
}