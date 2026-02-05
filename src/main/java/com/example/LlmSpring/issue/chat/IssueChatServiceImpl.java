package com.example.LlmSpring.issue.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueChatServiceImpl implements IssueChatService {

    private final IssueChatMapper chatMapper;

    @Transactional
    public IssueChatVO saveChat(IssueChatVO chatVO) {
        // 1. DB에 저장
        chatMapper.insertChat(chatVO);

        // 2. 저장된 후 (chatId 생성됨), 필요하다면 상세 정보를 다시 조회하거나
        //    현재 객체에 작성자 이름을 채워서 리턴해야 함.
        //    (성능을 위해 여기서는 Mapper가 insert 시 senderName을 못 가져오므로 별도 처리 혹은
        //     프론트에서 보낸 이름을 그대로 세팅해서 응답할 수도 있음. 여기선 심플하게 구현)
        return chatVO;
    }

    public List<IssueChatVO> getChatHistory(Integer issueId) {
        return chatMapper.selectChatListByIssueId(issueId);
    }
}