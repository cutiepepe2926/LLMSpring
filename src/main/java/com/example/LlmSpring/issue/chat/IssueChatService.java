package com.example.LlmSpring.issue.chat;

import java.util.List;

public interface IssueChatService {

    // 채팅 메시지 저장
    IssueChatVO saveChat(IssueChatVO chatVO);

    // 특정 이슈의 채팅 내역 전체 조회
    List<IssueChatVO> getChatHistory(Integer issueId);

}
