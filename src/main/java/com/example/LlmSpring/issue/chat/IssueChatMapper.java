package com.example.LlmSpring.issue.chat;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface IssueChatMapper {
    // 채팅 메시지 저장
    int insertChat(IssueChatVO chatVO);

    // 특정 이슈의 채팅 내역 조회 (작성자 이름 포함)
    List<IssueChatVO> selectChatListByIssueId(Integer issueId);
}