package com.mindlink.dto;

public class AiReportResponse {

    private String summary;
    private String recommendation;
    private String rawResponse;

    public AiReportResponse(String summary, String recommendation, String rawResponse) {
        this.summary = summary;
        this.recommendation = recommendation;
        this.rawResponse = rawResponse;
    }

    public String getSummary() { return summary; }
    public String getRecommendation() { return recommendation; }
    public String getRawResponse() { return rawResponse; }
}
