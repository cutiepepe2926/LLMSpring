package com.example.LlmSpring.report.finalreport;

import java.util.List;

public interface FinalReportService {
    String getOrCreateFinalReport(Long projectId, String reportType, List<String> selectedSections, String userId);
    List<FinalReportVO> getMyFinalReports(Long projectId, String userId);
    void updateFinalReport(Long finalReportId, String userId, String title, String content);
}
