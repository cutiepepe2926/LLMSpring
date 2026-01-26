package com.example.LlmSpring.dailyreport;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DailyReportMapper {
    //1. 해당 날짜, 해당 유저의 리포트가 있는지 확인 (중복 생성 방지)
    DailyReportVO selectReportByDate(@Param("projectId") Long projectId, @Param("userId") String userId, @Param("date") String date);

    //2. 리포트 생성 (초안)
    void insertReport(DailyReportVO vo);

    //3. 리포트 상세 조회
    DailyReportVO selectReportById(@Param("reportId") Long reportId);

    //4. 리포트 수정 (임시 저장)
    void updateReport(DailyReportVO vo);

    //5. 리포트 발행 (상태 변경)
    void updateReportPublishStatus(@Param("reportId") Long reportId, @Param("status") String status);

    //6. 채팅 로그 저장
    void insertChatLog(DailyReportChatLogVO vo);

    //7. 채팅 로그 조회
    List<DailyReportChatLogVO> selectChatLog(Long reportId);

    //8. 작성자 이름 조회
    String selectUserName(String userId);
}
