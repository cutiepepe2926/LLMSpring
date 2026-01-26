package com.example.LlmSpring.dailyreport;

import com.example.LlmSpring.dailyreport.response.DailyReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final DailyReportMapper dailyReportMapper;

    //1. 리포트 진입 (있으면 조회, 없으면 생성)
    @Transactional
    public DailyReportResponseDTO getOrCreateTodayReport(Long projectId, String userId){
        String today = LocalDate.now().toString();

        //1-1. 오늘 날짜로 이미 만든 리포트가 있는지 확인
        DailyReportVO existingReport = dailyReportMapper.selectReportByDate(projectId, userId, today);

        if (existingReport != null){
           return convertToDTO(existingReport);
        }

        //1-2. 없으면 새로 생성
        DailyReportVO newReport = new DailyReportVO();
        newReport.setProjectId(projectId);
        newReport.setUserId(userId);
        newReport.setReportDate(LocalDate.now());
        newReport.setTitle(LocalDate.now() + " 리포트");
        newReport.setContent("금일 진행한 업무 내용을 작성해주세요.");
        newReport.setCommitCount(0); //GitHub API 연동 시 실제 값 넣어야함

        dailyReportMapper.insertReport(newReport);

        return convertToDTO(newReport);
    }

    //2. 리포트 상세 조회
    public DailyReportResponseDTO getReportDetail(Long reportId) {
        DailyReportVO vo= dailyReportMapper.selectReportById(reportId);
        if(vo == null) throw new IllegalArgumentException("Report not found");

        DailyReportResponseDTO dto = convertToDTO(vo);

        //채팅 로그
        List<DailyReportChatLogVO> chatLogs = dailyReportMapper.selectChatLog(reportId);
        dto.setChatLogs(chatLogs);

        return dto;
    }

    //3. 리포트 임시 저장
    public void updateReport(Long reportId, String content, String title) {
        DailyReportVO vo = new DailyReportVO();
        vo.setReportId(reportId);
        vo.setTitle(title);
        vo.setContent(content);
        dailyReportMapper.updateReport(vo);
    }

    //4. 리포트 발행
    public void publishReport(Long reportId) {
        dailyReportMapper.updateReportPublishStatus(reportId, "PUBLISHED");
    }

    //VO -> DTO 변환
    private DailyReportResponseDTO convertToDTO(DailyReportVO vo){
        String userName = dailyReportMapper.selectUserName(vo.getUserId());
        return new DailyReportResponseDTO(vo, userName);
    }

}
