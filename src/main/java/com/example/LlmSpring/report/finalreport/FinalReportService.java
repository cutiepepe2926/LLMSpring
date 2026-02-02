package com.example.LlmSpring.report.finalreport;

public interface FinalReportService {
    String getOrCreateFinalReport(Long projectId, String reportType, String userId);
}
