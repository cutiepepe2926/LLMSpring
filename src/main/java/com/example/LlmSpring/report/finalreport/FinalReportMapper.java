package com.example.LlmSpring.report.finalreport;

import com.example.LlmSpring.report.dailyreport.DailyReportVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinalReportMapper {
    List<DailyReportVO> selectAllReportsByProjectId(Long projectId);
    FinalReportVO selectFinalReportByProjectId(Long projectId);
    void insertFinalReport(FinalReportVO finalReportVO);
}
