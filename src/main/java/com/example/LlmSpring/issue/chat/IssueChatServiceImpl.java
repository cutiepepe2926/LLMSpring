package com.example.LlmSpring.issue.chat;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.issue.IssueMapper;
import com.example.LlmSpring.issue.response.IssueDetailResponseDTO;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueChatServiceImpl implements IssueChatService {

    private final IssueChatMapper chatMapper;
    private final AlarmService alarmService;
    private final IssueMapper issueMapper;

    @Transactional
    public IssueChatVO saveChat(IssueChatVO chatVO) {
        // 1. DB에 저장
        chatMapper.insertChat(chatVO);

        // 2. 저장된 후 (chatId 생성됨), 필요하다면 상세 정보를 다시 조회하거나
        //    현재 객체에 작성자 이름을 채워서 리턴해야 함.
        //    (성능을 위해 여기서는 Mapper가 insert 시 senderName을 못 가져오므로 별도 처리 혹은
        //     프론트에서 보낸 이름을 그대로 세팅해서 응답할 수도 있음. 여기선 심플하게 구현)

        // 2. [추가] 알림 전송 로직
        try {
            // [수정] 기존에 존재하는 메서드를 사용하여 담당자 상세 정보 조회
            List<IssueDetailResponseDTO.AssigneeInfoDTO> assigneeDetails =
                    issueMapper.selectAssigneeDetailsByIssueId(chatVO.getIssueId());

            // 담당자 목록에서 ID만 추출 (Java Stream 사용)
            List<String> assigneeIds = assigneeDetails.stream()
                    .map(IssueDetailResponseDTO.AssigneeInfoDTO::getUserId) // DTO의 getter 사용
                    .collect(Collectors.toList());

            if (assigneeIds != null && !assigneeIds.isEmpty()) {
                alarmService.sendIssueChatAlarm(
                        chatVO.getUserId(),    // 보낸 사람
                        assigneeIds,           // 받는 사람 목록 (담당자들 ID 리스트)
                        chatVO.getProjectId(), // 프로젝트 ID
                        chatVO.getIssueId()    // 이슈 ID
                );
            }
        } catch (Exception e) {
            e.printStackTrace(); // 알림 실패가 채팅 저장을 막지 않도록 예외 처리
        }

        return chatVO;
    }

    public List<IssueChatVO> getChatHistory(Integer issueId) {
        return chatMapper.selectChatListByIssueId(issueId);
    }
}